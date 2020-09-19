package io.quiche4j.netty;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.quiche4j.Config;
import io.quiche4j.ConfigError;
import io.quiche4j.Connection;
import io.quiche4j.H3Config;
import io.quiche4j.H3Connection;
import io.quiche4j.H3Header;
import io.quiche4j.H3PollEvent;
import io.quiche4j.PacketHeader;
import io.quiche4j.PacketType;
import io.quiche4j.Quiche;
import io.quiche4j.Utils;

// xxx(okachaiev): this is almost one-to-one copy of Http3Server functionality
// wrapped into Netty channels + handlers infra. going to figure out proper
// API step-by-step while maintaining end-to-end flow fully functional
public class Http3Server {

    private static final int MAX_DATAGRAM_SIZE = 1350;
    private static final String SERVER_NAME = "Quiche4j";
    private static final byte[] SERVER_NAME_BYTES = SERVER_NAME.getBytes();
    private static final int SERVER_NAME_BYTES_LEN = SERVER_NAME_BYTES.length;

    private static final String HEADER_NAME_STATUS = ":status";
    private static final String HEADER_NAME_SERVER = "server";
    private static final String HEADER_NAME_CONTENT_LENGTH = "content-length";

    private final static class PartialResponse {
        private List<H3Header> headers;
        private byte[] body;
        private long written;

        PartialResponse(List<H3Header> headers, byte[] body, long written) {
            this.headers = headers;
            this.body = body;
            this.written = written;
        }
    }

    public static final class QuicAcceptEvent {
        private final Connection connection;
        private final InetSocketAddress sender;

        QuicAcceptEvent(Connection connection, InetSocketAddress sender) {
            this.connection = connection;
            this.sender = sender;
        }

        public final Connection connection() {
            return this.connection;
        }

        public final InetSocketAddress sender() {
            return this.sender;
        }
    }

    /// A few potential Netty-friendly API concepts:
    ///
    /// private abstract static class Http3FrameListener {
    /// abstract public void onHeaders(ChannelHandlerContext ctx, long streamId,
    /// Object[] headers);
    /// abstract public void onData(ChannelHandlerContext ctx, long streamId,
    /// ByteBuf data);
    /// abstract public void onFinished(ChannelHandlerContext ctx, long streamId);
    /// }
    ///
    /// private static void sendResponse(ChannelHandlerContext ctx, Http3FrameStream
    /// stream, ByteBuf payload) {
    /// Http3Headers headers = new DefaultHttp3Headers().status(OK.codeAsText());
    /// ctx.write(new DefaultHttp3HeadersFrame(headers).stream(stream));
    /// ctx.write(new DefaultHttp3DataFrame(payload, true).stream(stream));
    /// }
    ///
    /// or... we can go even further and reuse parts of HTTP2 implementation in
    /// Netty. for example, DefaultHttp2Headers, Http2HeadersFrame, Http2DataFrame,
    /// and others are still perfectly valid as long as we replace encoder/decoder
    /// (to deal with QPACK instead of HPACK)

    /// so...
    ///
    /// technically, we need to have a multiplexor handler that
    /// gets all packets and maintains mapping between connection ids
    /// to channels. this way, `childHandler` would receive only
    /// UDP packets relevant to an established connection.
    ///
    /// to get to this point, we will keep client map the same way
    /// it's done in io.quiche4j.examples.Http3Server
    private final static class QuicConnectionHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        private final Config config;
        private final byte[] connIdSeed;

        private Connection conn = null;

        QuicConnectionHandler(Config config, byte[] connIdSeed) {
            this.config = config;
            this.connIdSeed = connIdSeed;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            System.out.println("> channel read0 " + ctx.channel());

            // PARSE QUIC HEADER
            final byte[] hdrContent = new byte[packet.content().readableBytes()];
            packet.content().readBytes(hdrContent);
            final PacketHeader hdr = PacketHeader.parse(hdrContent, Quiche.MAX_CONN_ID_LEN);
            System.out.println("> packet " + hdr);

            // xxx(okachaiev): what about connection migration, hah?
            if (null == conn)
                negotiateAndAccept(ctx, hdr, packet.sender());
            // still nothing, need more packets
            if (null == conn)
                return;

            // POTENTIALLY COALESCED PACKETS
            final int read = conn.recv(hdrContent);
            if (read < 0 && read != Quiche.ERROR_CODE_DONE) {
                System.out.println("> recv failed " + read);
                return;
            }

            System.out.println("> conn.recv " + read + " bytes");
            System.out.println("> conn.established " + conn.isEstablished());

            final byte[] out = new byte[MAX_DATAGRAM_SIZE];
            while (true) {
                final int len = conn.send(out);
                if (len < 0 && len != Quiche.ERROR_CODE_DONE) {
                    System.out.println("! conn.send failed " + len);
                    break;
                }
                if (len <= 0)
                    break;
                System.out.println("> conn.send " + len + " bytes");

                final ByteBuf content = ctx.alloc().buffer(len);
                content.writeBytes(out, 0, len);
                ctx.write(new DatagramPacket(content, packet.sender()));
            }
            ctx.flush();

            // xxx(okachaiev): what about Early Data? :thinking:
            if (conn.isEstablished()) {
                System.out.println("> handshake done");

                // so, here we have a connection ready to go we can remove
                // this handler from the pipeline. the rest should be done
                // by other handlers, e.g. Http3FrameCodec or Http3MultiplexHandler
                // or.. on the other hand we can setup Http3FrameCodec
                // to execute connection negotiation phase first and
                // pass `Connection` object by directly instantiating new a handler
                // or.. send it as a user trigger? :thinking:
                ctx.pipeline().remove(this);
                ctx.fireUserEventTriggered(new QuicAcceptEvent(conn, packet.sender()));
            }

            // CLEANUP CLOSED CONNS
            // xxx(okachaiev): that's weird we're doing this only on READ
            // moreover... we should be able to detect CLOSE events on
            // child streams and wrap them into Future + setup callback
            // to cleanup properly
            // cleanupClosedConnections();
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
        }

        private final void negotiateAndAccept(ChannelHandlerContext ctx, PacketHeader hdr, InetSocketAddress sender) {
            if (PacketType.INITIAL != hdr.getPacketType()) {
                System.out.println("! wrong packet type");
                // tricky part here... typically, in this case we would
                // force close connection as it's in weird state. but here...
                // there's no connection so far, just a bunch of UDP packets
                // we might throw exception for other handlers to deal with
                // this but it's unclear what you can actually do about it
                // maybe ban an IP address when too-noisy? :thinking:
                return;
            }

            // NEGOTIATE VERSION
            if (!Quiche.versionIsSupported(hdr.getVersion())) {
                System.out.println("> version negotiation");

                final byte[] out = new byte[MAX_DATAGRAM_SIZE];
                final int negLength = Quiche.negotiateVersion(hdr.getSourceConnectionId(),
                        hdr.getDestinationConnectionId(), out);
                if (negLength < 0) {
                    System.out.println("! failed to negotiate version " + negLength);
                    return;
                }
                final ByteBuf content = ctx.alloc().ioBuffer(negLength, negLength);
                content.writeBytes(out, 0, negLength);
                ctx.writeAndFlush(new DatagramPacket(content, sender));
                return;
            }

            // SIGN CONN ID
            final byte[] connId = Quiche.signConnectionId(connIdSeed, hdr.getDestinationConnectionId());

            // RETRY IF TOKEN IS EMPTY
            if (null == hdr.getToken()) {
                System.out.println("> stateless reset");

                // xxx(okachaiev): avoid allocations
                final byte[] out = new byte[MAX_DATAGRAM_SIZE];
                final byte[] token = mintToken(hdr, sender.getAddress());
                final int retryLength = Quiche.retry(hdr.getSourceConnectionId(), hdr.getDestinationConnectionId(),
                        connId, token, hdr.getVersion(), out);
                if (retryLength < 0) {
                    System.out.println("! retry failed " + retryLength);
                    return;
                }

                System.out.println("> retry length " + retryLength);

                final ByteBuf content = ctx.alloc().buffer(retryLength, retryLength);
                content.writeBytes(out, 0, retryLength);
                ctx.writeAndFlush(new DatagramPacket(content, sender));
                return;
            }

            // VALIDATE TOKEN
            final byte[] odcid = validateToken(sender.getAddress(), hdr.getToken());
            if (null == odcid) {
                System.out.println("! invalid address validation token");
                return;
            }

            byte[] sourceConnId = connId;
            final byte[] destinationConnId = hdr.getDestinationConnectionId();
            if (sourceConnId.length != destinationConnId.length) {
                System.out.println("! invalid destination connection id");
                return;
            }
            sourceConnId = destinationConnId;

            // ACCEPT
            conn = Quiche.accept(sourceConnId, odcid, config);

            System.out.println("> new connection " + Utils.asHex(sourceConnId));
        }

        // private void cleanupClosedConnections() {
        // for(String connId: clients.keySet()) {
        // if(clients.get(connId).getConnection().isClosed()) {
        // System.out.println("> cleaning up " + connId);
        // clients.remove(connId);
        // System.out.println("! # of clients: " + clients.size());
        // }
        // }
        // }
    }

    static interface DefaultHttp3Frame {
        public long stream();
    }

    // xxx(okachaiev): retain and its friends :(
    final static class DefaultHttp3DataFrame implements DefaultHttp3Frame {
        private ByteBuf content;
        private boolean isEndStream;
        private long streamId;

        DefaultHttp3DataFrame(boolean isEndStream) {
            this(Unpooled.EMPTY_BUFFER, isEndStream);
        }

        DefaultHttp3DataFrame(ByteBuf content) {
            this(content, false);
        }

        DefaultHttp3DataFrame(ByteBuf content, boolean isEndStream) {
            this.content = content;
            this.isEndStream = isEndStream;
        }

        public ByteBuf content() {
            return this.content;
        }

        public boolean isEndStream() {
            return this.isEndStream;
        }

        public DefaultHttp3DataFrame stream(long streamId) {
            this.streamId = streamId;
            return this;
        }

        public long stream() {
            return this.streamId;
        }
    }

    final static class DefaultHttp3HeadersFrame implements DefaultHttp3Frame {
        final private List<H3Header> headers;
        private long streamId;

        DefaultHttp3HeadersFrame(List<H3Header> headers) {
            this.headers = headers;
        }

        public List<H3Header> headers() {
            return this.headers;
        }

        public DefaultHttp3HeadersFrame stream(long streamId) {
            this.streamId = streamId;
            return this;
        }

        public long stream() {
            return this.streamId;
        }
    }

    final static class Http3FrameCodec extends SimpleChannelInboundHandler<DatagramPacket>
            implements ChannelOutboundHandler {

        private final H3Config h3Config;
        // xxx(okachaiev): replace with pending writes queue
        private final HashMap<Long, PartialResponse> partialResponses;

        private H3Connection h3Conn = null;
        private Connection conn = null;
        private InetSocketAddress sender = null;

        Http3FrameCodec(H3Config config) {
            this.h3Config = config;
            this.partialResponses = new HashMap<>();
        }

        // xxx(okachaiev): feels like this should be another handler
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            super.userEventTriggered(ctx, evt);
            if (evt instanceof QuicAcceptEvent) {
                final QuicAcceptEvent accept = (QuicAcceptEvent) evt;
                conn = accept.connection();
                sender = accept.sender();
                h3Conn = H3Connection.withTransport(accept.connection(), h3Config);
                System.out.println("> new H3 connection " + h3Conn);
            } else {
                ctx.fireUserEventTriggered(evt);
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
            System.out.println("> channel read0 " + ctx.channel());

            // PROCESS WRITABLES
            // xxx(okachaiev): this would look different for cases
            // of multiplexed vs. unified downstream handlers :thinking:
            // xxx(okachaiev): processing writables only on READ events
            // is a total trap
            // xxx(okachaiev): Http3StreamVisitor ???
            // conn.writable().forEach(streamId -> {
            // handleWritable(current, streamId);
            // });

            final byte[] content = new byte[msg.content().readableBytes()];
            msg.content().readBytes(content);

            final int read = conn.recv(content);
            if (read < 0 && read != Quiche.ERROR_CODE_DONE) {
                System.out.println("> recv failed " + read);
                return;
            }

            // H3 POLL
            final List<H3Header> headers = new ArrayList<>();
            Long streamId = 0L;
            // xxx(okachaiev): while true might not be the best idea
            // as we want to deal with all connected clients rather
            // then looping over a single one until it's done
            while (true) {
                streamId = h3Conn.poll(new H3PollEvent() {
                    public void onHeader(long streamId, String name, String value) {
                        headers.add(new H3Header(name, value));
                        System.out.println("< got header " + name + " on " + streamId);
                    }

                    public void onData(long streamId) {
                        System.out.println("< got data on " + streamId);

                        final ByteBuf content = ctx.alloc().buffer();
                        ctx.fireChannelRead(new DefaultHttp3DataFrame(content).stream(streamId));
                    }

                    public void onFinished(long streamId) {
                        System.out.println("< finished " + streamId);

                        ctx.fireChannelRead(new DefaultHttp3DataFrame(true).stream(streamId));
                    }
                });

                if (streamId < 0 && streamId != Quiche.ERROR_CODE_DONE) {
                    System.out.println("! poll failed " + streamId);

                    break;
                }

                if (Quiche.ERROR_CODE_DONE == streamId)
                    break;

                // xxx(okachaiev): this won't be necessary as soon as I fix
                // the API for Quiche4j h3 polling mechanism
                if (0 < headers.size()) {
                    ctx.fireChannelRead(new DefaultHttp3HeadersFrame(headers).stream(streamId));
                }
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            System.out.println(msg);
            final long streamId = ((DefaultHttp3Frame) msg).stream();

            if (msg instanceof DefaultHttp3HeadersFrame) {
                final DefaultHttp3HeadersFrame headersFrame = (DefaultHttp3HeadersFrame) msg;
                final long sent = h3Conn.sendResponse(streamId, headersFrame.headers(), false);
                if (sent == Quiche.ERROR_CODE_H3_STREAM_BLOCKED) {
                    // STREAM BLOCKED
                    System.out.print("> stream " + streamId + " blocked");
        
                    // STASH PARTIAL RESPONSE
                    final PartialResponse part = new PartialResponse(headersFrame.headers(), new byte[] {}, 0L);
                    partialResponses.put(streamId, part);
                    return;
                }
                if (sent < 0) {
                    System.out.println("! h3.send response failed " + sent);

                    return;
                }
            } else if (msg instanceof DefaultHttp3DataFrame) {
                final byte[] body = ByteBufUtil.getBytes(((DefaultHttp3DataFrame) msg).content());
                if (body.length == 0) {
                    // this means we're done with the stream
                    // xxx(okachaiev): close? :thinking:
                    return;
                }

                final long written = h3Conn.sendBody(streamId, body, ((DefaultHttp3DataFrame) msg).isEndStream());
                if (written < 0) {
                    System.out.println("! h3 send body failed " + written);
                    return;
                }

                System.out.println("> send body " + written + " body");
        
                if (written < body.length) {
                    // STASH PARTIAL RESPONSE
                    final PartialResponse part = new PartialResponse(null, body, written);
                    partialResponses.put(streamId, part);
                }
            }

            // writables processing should be done... somewhere else
            final byte[] out = new byte[MAX_DATAGRAM_SIZE];
            while (true) {
                final int len = conn.send(out);
                if (len < 0 && len != Quiche.ERROR_CODE_DONE) {
                    System.out.println("! conn.send failed " + len);
                    break;
                }
                if (len <= 0)
                    break;
                System.out.println("> conn.send " + len + " bytes");

                final ByteBuf content = ctx.alloc().buffer(len);
                content.writeBytes(out, 0, len);
                ctx.write(new DatagramPacket(content, this.sender));
            }
            ctx.flush();
        }

        @Override
        public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise)
                throws Exception {
            ctx.bind(localAddress, promise);
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) throws Exception {
                    ctx.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            ctx.disconnect(promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            ctx.close(promise);
        }

        @Override
        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            ctx.deregister(promise);
        }

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            ctx.read();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

    }

    // xxx(okachaiev): should be a subclass of Http3FrameListener instead of
    // low-level impl.
    final static class MyHttp3ServerResponseHandler extends SimpleChannelInboundHandler<DefaultHttp3Frame> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DefaultHttp3Frame frame) {
            // we do not expect body, so close the stream immediatly
            //
            // SHUTDOWN STREAM
            // conn.streamShutdown(streamId, Quiche.Shutdown.READ, 0L);
            //
            // xxx(okachaiev): Netty-friendly API should look like...
            //
            // final AbstractHttp3StreamChannel ch = new Http3MultiplexHandlerStreamChannel(conn, streamId);
            // ch.closeInbound();

            // xxx(okachaiev): Netty-friendly API should look like...
            //
            // Http3Headers headers = new DefaultHttp3Headers().status(OK.codeAsText());
            // ctx.write(new DefaultHttp3HeadersFrame(headers).stream(stream));
            // ctx.write(new DefaultHttp3DataFrame(payload, true).stream(stream));

            final CharSequence body = "Hello world";

            final List<H3Header> headers = new ArrayList<>();
            headers.add(new H3Header(HEADER_NAME_STATUS, "200"));
            headers.add(new H3Header(HEADER_NAME_SERVER, SERVER_NAME));
            headers.add(new H3Header(HEADER_NAME_CONTENT_LENGTH, Integer.toString(body.length())));
            ctx.write(new DefaultHttp3HeadersFrame(headers).stream(frame.stream()));

            final ByteBuf content = ByteBufUtil.writeAscii(ctx.alloc(), body);
            ctx.writeAndFlush(new DefaultHttp3DataFrame(content, true).stream(frame.stream()));
        }

    }

    public static void main(String[] args) throws InterruptedException, IOException, ConfigError {
        final int port = 4433;
        final NioEventLoopGroup group = new NioEventLoopGroup();

        final Config config = Config.newInstance(Quiche.PROTOCOL_VERSION);

        config.setApplicationProtos(Quiche.H3_APPLICATION_PROTOCOL);
        config.verityPeer(false);
        config.loadCertChainFromPemFile(Utils.copyFileFromJAR("certs", "/cert.crt"));
        config.loadPrivKeyFromPemFile(Utils.copyFileFromJAR("certs", "/cert.key"));
        config.setMaxIdleTimeout(5_000);
        config.setMaxUdpPayloadSize(MAX_DATAGRAM_SIZE);
        config.setInitialMaxData(10_000_000);
        config.setInitialMaxStreamDataBidiLocal(1_000_000);
        config.setInitialMaxStreamDataBidiRemote(1_000_000);
        config.setInitialMaxStreamDataUni(1_000_000);
        config.setInitialMaxStreamsBidi(100);
        config.setInitialMaxStreamsUni(100);
        config.setDisableActiveMigration(true);
        config.enableEarlyData();

        final byte[] seed = Quiche.newConnectionIdSeed();
        final H3Config h3Config = H3Config.newInstance();

        try {
            final Bootstrap b = new Bootstrap();
            // xxx(okachaiev): SO_TIMEOUT, please
            b.group(group).channel(NioDatagramChannel.class).option(ChannelOption.SO_BROADCAST, true)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        public void initChannel(final NioDatagramChannel ch) {
                            ch.pipeline().addLast(new QuicConnectionHandler(config, seed),
                                    new Http3FrameCodec(h3Config),
                                    new MyHttp3ServerResponseHandler());
                        }
                    });

            b.bind(port).sync().channel().closeFuture().await();
        } finally {
            group.shutdownGracefully();
        }

    }

    /**
     * Generate a stateless retry token.
     * 
     * The token includes the static string {@code "Quiche4j"} followed by the IP
     * address of the client and by the original destination connection ID generated
     * by the client.
     * 
     * Note that this function is only an example and doesn't do any cryptographic
     * authenticate of the token. *It should not be used in production system*.
     */
    public final static byte[] mintToken(PacketHeader hdr, InetAddress address) {
        final byte[] addr = address.getAddress();
        final byte[] dcid = hdr.getDestinationConnectionId();
        final int total = SERVER_NAME_BYTES_LEN + addr.length + dcid.length;
        final ByteBuffer buf = ByteBuffer.allocate(total);
        buf.put(SERVER_NAME_BYTES);
        buf.put(addr);
        buf.put(dcid);
        return buf.array();
    }

    public final static byte[] validateToken(InetAddress address, byte[] token) {
        if (token.length <= 8)
            return null;
        if (!Arrays.equals(SERVER_NAME_BYTES, Arrays.copyOfRange(token, 0, SERVER_NAME_BYTES_LEN)))
            return null;
        final byte[] addr = address.getAddress();
        if (!Arrays.equals(addr, Arrays.copyOfRange(token, SERVER_NAME_BYTES_LEN, addr.length + SERVER_NAME_BYTES_LEN)))
            return null;
        return Arrays.copyOfRange(token, SERVER_NAME_BYTES_LEN + addr.length, token.length);
    }

    // public final static void handleWritable(Client client, long streamId) {
    //     final PartialResponse resp = client.partialResponses.get(streamId);
    //     if (null == resp)
    //         return;

    //     final H3Connection h3 = client.getH3Connection();
    //     if (null != resp.headers) {
    //         final long sent = h3.sendResponse(streamId, resp.headers, false);
    //         if (sent == Quiche.ERROR_CODE_H3_STREAM_BLOCKED)
    //             return;
    //         if (sent < 0) {
    //             System.out.println("! h3.send response failed " + sent);
    //             return;
    //         }
    //     }

    //     resp.headers = null;

    //     final byte[] body = Arrays.copyOfRange(resp.body, (int) resp.written, resp.body.length);
    //     final long written = h3.sendBody(streamId, body, true);
    //     if (written < 0 && written != Quiche.ERROR_CODE_DONE) {
    //         System.out.println("! h3 send body failed " + written);
    //         return;
    //     }

    //     System.out.println("> send body " + written + " body");

    //     resp.written += written;
    //     if (resp.written < resp.body.length) {
    //         client.partialResponses.remove(streamId);
    //     }
    // }
}