package io.quiche4j.examples;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.internal.PlatformDependent;
import io.quiche4j.Config;
import io.quiche4j.ConfigBuilder;
import io.quiche4j.Connection;
import io.quiche4j.ConnectionFailureException;
import io.quiche4j.Quiche;
import io.quiche4j.Utils;
import io.quiche4j.http3.Http3;
import io.quiche4j.http3.Http3Config;
import io.quiche4j.http3.Http3ConfigBuilder;
import io.quiche4j.http3.Http3Connection;
import io.quiche4j.http3.Http3EventListener;
import io.quiche4j.http3.Http3Header;

public class Http3NettyClient {
    public static final int MAX_DATAGRAM_SIZE = 1350;
    public static final Object HANDSHAKE_DONE = new Object();
    public static final String CLIENT_NAME = "Quiche4j";

    public final static class DatagramEncoder extends MessageToMessageEncoder<ByteBuf> {
        final InetSocketAddress recipient;

        DatagramEncoder(InetSocketAddress recipient) {
            this.recipient = recipient;
        }

        @Override
        public void encode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            out.add(new DatagramPacket(in.retain(), this.recipient));
        }
    }

    public final static DatagramDecoder DATAGRAM_DECODER_INSTANCE = new DatagramDecoder();

    @Sharable
    public final static class DatagramDecoder extends MessageToMessageDecoder<DatagramPacket> {
        @Override
        protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {
            out.add(msg.content().retain());
        }
    }

    public final static class QuicHandshakeHandler extends ChannelInboundHandlerAdapter {
        private final static Throwable HANDSHAKE_FAILURE = new RuntimeException("Handshake failed");

        ChannelHandlerContext ctx;
        final Connection connection;
        final ChannelPromise handshakePromise;
        final InetSocketAddress remoteAddress;

        QuicHandshakeHandler(Connection connection, ChannelPromise handshakePromise, InetSocketAddress remoteAddress) {
            this.connection = connection;
            this.handshakePromise = handshakePromise;
            this.remoteAddress = remoteAddress;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        public ChannelPromise handshakePromise() {
            return this.handshakePromise;
        }

        public ChannelPromise startHandshake() {
            final byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
            final int len = this.connection.send(buffer);
            if (len < 0 && len != Quiche.ErrorCode.DONE) {
                this.handshakePromise.setFailure(HANDSHAKE_FAILURE);
                ctx.pipeline().remove(this);
                ctx.channel().close();
            } else {
                final ByteBuf buf = ctx.alloc().buffer(len);
                buf.writeBytes(buffer, 0, len);
                ctx.writeAndFlush(buf);
            }
            return this.handshakePromise;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            final ByteBuf buffer = (ByteBuf) msg;
            try {
                final byte[] buf = new byte[buffer.readableBytes()];
                buffer.readBytes(buf);
                this.connection.recv(buf, this.remoteAddress);
                if (this.connection.isClosed()) {
                    this.handshakePromise.setFailure(HANDSHAKE_FAILURE);
                    ctx.pipeline().remove(this);
                    ctx.channel().close();
                } else if (this.connection.isEstablished()) {
                    this.handshakePromise.setSuccess();
                    ctx.pipeline().remove(this);
                    ctx.fireUserEventTriggered(HANDSHAKE_DONE);
                }
            } finally {
                buffer.release();
            }
        }
    }

    public final static class HttpOverQuicHandler extends ChannelDuplexHandler {

        private final Connection connection;
        private final Http3Config config;
        private final Map<Long, ChannelPromise> streamResponseMap;
        private final AtomicLong lastStreamId;
        private final InetSocketAddress remoteAddress;

        ChannelHandlerContext context;
        Http3Connection http3Connection;
        Http3EventListener listener;

        HttpOverQuicHandler(Connection connection, Http3Config config, InetSocketAddress remoteAddress) {
            this.connection = connection;
            this.config = config;
            this.streamResponseMap = PlatformDependent.newConcurrentHashMap();
            this.lastStreamId = new AtomicLong(0);
            this.remoteAddress = remoteAddress;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
            this.context = ctx;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
            if (event == HANDSHAKE_DONE) {
                http3Connection = Http3Connection.withTransport(connection, config);
                final Http3Connection h3c = http3Connection;
                this.listener = new Http3EventListener() {
                    public void onHeaders(long streamId, List<Http3Header> headers, boolean hasBody) {
                        System.out.println("< got headers for " + streamId);
                        headers.forEach(header -> {
                            System.out.println(header.name() + ": " + header.value());
                        });
                    }

                    public void onData(long streamId) {
                        final byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
                        final int len = h3c.recvBody(streamId, buffer);
                        if (len > 0) {
                            System.out.println("< got body " + len + " bytes for " + streamId);
                            final byte[] body = Arrays.copyOfRange(buffer, 0, len);

                            System.out.println(new String(body, StandardCharsets.UTF_8));
                        }
                    }

                    public void onFinished(long streamId) {
                        System.out.println("> response finished");

                        ChannelPromise promise = streamResponseMap.get(streamId);
                        if (null != promise) {
                            promise.setSuccess();
                        }
                    }
                };
            } else {
                ctx.fireUserEventTriggered(event);
            }
        }

        // xxx(okachaiev): proper handling of write promise would be quite an
        // interesting challenge...
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            final FullHttpRequest request = (FullHttpRequest) msg;
            try {
                List<Http3Header> headers = new ArrayList<>();
                headers.add(new Http3Header(":method", request.method().name()));
                headers.add(new Http3Header(":scheme", request.headers().get(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text())));
                headers.add(new Http3Header(":authority", request.headers().get(HttpHeaderNames.HOST)));
                headers.add(new Http3Header(":path", request.uri()));
                headers.add(new Http3Header("user-agent", CLIENT_NAME));
                headers.add(new Http3Header("content-length", "0"));
                http3Connection.sendRequest(headers, true);
            } finally {
                request.release();
            }
        }

        private void writeOutbound(ChannelHandlerContext ctx) {
            int len;
            final byte[] buf = new byte[MAX_DATAGRAM_SIZE];
            while(true) {
                len = connection.send(buf);
                if (len < 0) {
                    break;
                }
                final ByteBuf buffer = ctx.alloc().buffer(len);
                buffer.writeBytes(buf, 0, len);
                ctx.write(buffer);
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            this.writeOutbound(ctx);
            ctx.flush();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            final ByteBuf content = (ByteBuf) msg;
            try {
                if (content.readableBytes() > 0) {
                    final byte[] buf = new byte[content.readableBytes()];
                    content.readBytes(buf);
                    final int read = this.connection.recv(buf, this.remoteAddress);
                    if (read > 0) {
                        while(true) {
                            final long streamId = http3Connection.poll(this.listener);
                            if (streamId < 0) break;
                        }
                        this.writeOutbound(ctx);
                    }
                }
            } finally {
                content.release();
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            super.channelReadComplete(ctx);
            this.writeOutbound(ctx);
            ctx.flush();

            if (connection.isClosed()) {
                ctx.channel().close();
            }
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            if (!connection.isClosed()) {
                this.connection.close(true, 0x00, "kthxbye");
                this.writeOutbound(ctx);
                ctx.flush();
            }

            System.out.println(this.connection.stats());

            streamResponseMap.values().stream().forEach(entry -> {
                entry.setFailure(new ClosedChannelException());
            });

            ctx.close(promise);
        }

        private long nextStreamId() {
            return this.lastStreamId.getAndAdd(2);
        }

        public long sendRequest(FullHttpRequest request) {
            final long streamId = nextStreamId();
            // xxx(okachaiev): to avoid this weird .channe().write() mechanic
            // we need to make sure that user-facing API works on a separate
            // handler (which is setup after Http3 codec layer)
            this.context.channel().writeAndFlush(request);
            streamResponseMap.put(streamId, this.context.newPromise());
            return streamId;
        }

        public void awaitStreamResponse(long streamId, long timeout, TimeUnit unit) {
            ChannelPromise promise = streamResponseMap.get(streamId);
            if (null == promise) {
                throw new IllegalStateException("Write operation never happened?");
            }

            if (!promise.awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Waiting on reponse failed");
            }

            if (!promise.isSuccess()) {
                throw new RuntimeException(promise.cause());
            }

            streamResponseMap.remove(streamId);
        }
    }

    public final static class Http3ClientInitializer extends ChannelInitializer<DatagramChannel> {
        private final InetSocketAddress recipient;
        private final String domain;
        private final Config config;
        private final Http3Config http3config;
        private QuicHandshakeHandler handshaker;
        private HttpOverQuicHandler httpHandler;

        public Http3ClientInitializer(InetSocketAddress address, String domain, Config config, Http3Config http3config) {
            this.recipient = address;
            this.domain = domain;
            this.config = config;
            this.http3config = http3config;
        }

        @Override
        protected void initChannel(DatagramChannel ch) throws ConnectionFailureException {
            final byte[] connId = Quiche.newConnectionId();
            final Connection conn = Quiche.connect(this.domain, connId, recipient, this.config);
            this.handshaker = new QuicHandshakeHandler(conn, ch.newPromise(), ch.remoteAddress());
            this.httpHandler = new HttpOverQuicHandler(conn, http3config, ch.remoteAddress());

            ch.pipeline().addLast(
                new DatagramEncoder(recipient),
                DATAGRAM_DECODER_INSTANCE,
                this.handshaker,
                httpHandler);
        }

        public QuicHandshakeHandler handshaker() {
            return this.handshaker;
        }

        public HttpOverQuicHandler httpHandler() {
            return this.httpHandler;
        }
    }

    public static void main(String[] args) throws IOException {
        if (0 == args.length) {
            System.out.println("Usage: ./http3-netty-client.sh <URL>");
            System.exit(1);
        }

        final String url = args[0];
        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            System.out.println("Failed to parse URL " + url);
            System.exit(1);
            return;
        }

        final InetSocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort());

        final EventLoopGroup workerGroup = new NioEventLoopGroup();
        final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
            .withApplicationProtos(Http3.APPLICATION_PROTOCOL)
            .withVerifyPeer(true)
            .loadCertChainFromPemFile(Utils.copyFileFromJAR("certs", "/cert.crt"))
            .loadPrivKeyFromPemFile(Utils.copyFileFromJAR("certs", "/cert.key"))
            .withMaxIdleTimeout(5_000)
            .withMaxUdpPayloadSize(MAX_DATAGRAM_SIZE)
            .withInitialMaxData(10_000_000)
            .withInitialMaxStreamDataBidiLocal(1_000_000)
            .withInitialMaxStreamDataBidiRemote(1_000_000)
            .withInitialMaxStreamDataUni(1_000_000)
            .withInitialMaxStreamsBidi(100)
            .withInitialMaxStreamsUni(100)
            .withDisableActiveMigration(true)
            .build();
        final Http3Config http3config = new Http3ConfigBuilder().build();

        final Http3ClientInitializer initializer =
            new Http3ClientInitializer(address, uri.getHost(), config, http3config);

        try {
            final Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioDatagramChannel.class);
            b.handler(initializer);
    
            final Channel ch = b.bind(0).syncUninterruptibly().channel();
            initializer.handshaker().startHandshake().syncUninterruptibly();

            // connection is now ready
            System.out.println("Connection was succesfully established");

            final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, uri.getPath(), Unpooled.EMPTY_BUFFER);
            request.headers().add(HttpHeaderNames.HOST, uri.getHost());
            request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");

            long streamId = initializer.httpHandler().sendRequest(request);

            System.out.println("Waiting on stream " + streamId);

            initializer.httpHandler().awaitStreamResponse(streamId, 10, TimeUnit.SECONDS);
            ch.close();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}