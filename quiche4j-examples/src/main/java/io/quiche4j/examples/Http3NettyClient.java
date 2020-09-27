package io.quiche4j.examples;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
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
            System.out.println("WRITE PACKET " + in.readableBytes() + " bytes; " + this.recipient);
            out.add(new DatagramPacket(in.retain(), this.recipient, null));
        }
    }

    public final static class DatagramDecoder extends MessageToMessageDecoder<DatagramPacket> {
        @Override
        protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) {
            System.out.println("GOT PACKET " + msg.content().readableBytes());
            out.add(msg.content().retain());
        }
    }

    public final static class QuicHandshakeHandler extends ChannelInboundHandlerAdapter {
        private final static Throwable HANDSHAKE_FAILURE = new RuntimeException("Handshake failed");

        ChannelHandlerContext ctx;
        final Connection connection;
        final ChannelPromise handshakePromise;

        QuicHandshakeHandler(Connection connection, ChannelPromise handshakePromise) {
            this.connection = connection;
            this.handshakePromise = handshakePromise;
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
            } else {
                final ByteBuf buf = ctx.alloc().buffer(len);
                buf.writeBytes(buffer, 0, len);
                ctx.writeAndFlush(buf);
            }
            return this.handshakePromise;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            System.out.println("READ PACKET");
            final ByteBuf buffer = (ByteBuf) msg;
            final byte[] buf = new byte[buffer.readableBytes()];
            buffer.readBytes(buf);
            final int read = this.connection.recv(buf);
            System.out.println("READ PACKET " + read);
            if (this.connection.isClosed()) {
                this.handshakePromise.setFailure(HANDSHAKE_FAILURE);
                ctx.pipeline().remove(this);
            } else if (this.connection.isEstablished()) {
                this.handshakePromise.setSuccess();
                ctx.pipeline().remove(this);
                ctx.fireUserEventTriggered(HANDSHAKE_DONE);
            }
        }
    }

    public final static class HttpOverQuicHandler extends ChannelDuplexHandler {

        private final Connection connection;
        private final Http3Config config;
        private final Map<Long, Entry<ChannelFuture, ChannelPromise>> streamResponseMap;
        private final AtomicLong lastStreamId;

        ChannelHandlerContext context;
        Http3Connection http3Connection;
        Http3EventListener listener;

        HttpOverQuicHandler(Connection connection, Http3Config config) {
            this.connection = connection;
            this.config = config;
            this.streamResponseMap = PlatformDependent.newConcurrentHashMap();
            this.lastStreamId = new AtomicLong(0);
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.context = ctx;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
            if (event == HANDSHAKE_DONE) {
                http3Connection = Http3Connection.withTransport(connection, config);
                final Http3Connection h3c = http3Connection;
                this.listener = new Http3EventListener() {
                    public void onHeaders(long streamId, List<Http3Header> headers, boolean hasBody) {
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

                        Entry<ChannelFuture, ChannelPromise> entry = streamResponseMap.get(streamId);
                        if (null != entry) {
                            entry.getValue().setSuccess();
                        }
                    }
                };
            } else {
                ctx.fireUserEventTriggered(event);
            }
        }

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
                System.out.println("WRITE HTTP3 PACKET " + len);
                final ByteBuf buffer = ctx.alloc().buffer(len);
                buffer.writeBytes(buf, 0, len);
                ctx.write(buf);
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
                    final int read = this.connection.recv(buf);
                    System.out.println("READ HTTP3 RECV " + read);
                    if (read > 0) {
                        if(http3Connection.poll(this.listener) < 0) {
                            this.writeOutbound(ctx);
                        }
                    }
                }
            } finally {
                content.release();
            }

            if (connection.isClosed()) {
                ctx.channel().close();
            }
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            super.close(ctx, promise);
            streamResponseMap.values().stream().forEach(entry -> {
                entry.getValue().setFailure(new ClosedChannelException());
            });
        }

        private long nextStreamId() {
            return this.lastStreamId.getAndAdd(2);
        }

        public long sendRequest(FullHttpRequest request) {
            final long streamId = nextStreamId();
            final ChannelFuture writeFuture = this.context.channel().writeAndFlush(request);
            streamResponseMap.put(streamId, new SimpleEntry<>(writeFuture, this.context.newPromise()));
            return streamId;
        }

        public void awaitStreamResponse(long streamId, long timeout, TimeUnit unit) {
            Entry<ChannelFuture, ChannelPromise> entry = streamResponseMap.get(streamId);
            if (null == entry) {
                throw new IllegalStateException("Write operation never happened?");
            }

            if (!entry.getKey().awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Waiting on write failed");
            }

            if (!entry.getKey().isSuccess()) {
                throw new RuntimeException(entry.getKey().cause());
            }

            if (!entry.getValue().awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Waiting on reponse failed");
            }

            if (!entry.getValue().isSuccess()) {
                throw new RuntimeException(entry.getValue().cause());
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
            final Connection conn = Quiche.connect(this.domain, connId, this.config);
            this.handshaker = new QuicHandshakeHandler(conn, ch.newPromise());
            this.httpHandler = new HttpOverQuicHandler(conn, http3config);

            ch.pipeline().addLast(
                new DatagramEncoder(recipient),
                new DatagramDecoder(),
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

        final String hostname = "quic.tech";
        final InetSocketAddress address = new InetSocketAddress(hostname, 8443);
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
            new Http3ClientInitializer(address, hostname, config, http3config);

        try {
            final Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioDatagramChannel.class);
            b.handler(initializer);
    
            b.bind(0).syncUninterruptibly();
            initializer.handshaker().startHandshake().syncUninterruptibly();

            // connection is now ready
            System.out.println("Connection was succesfully established");

            final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/", Unpooled.EMPTY_BUFFER);
            request.headers().add(HttpHeaderNames.HOST, "quic.tech");
            request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "https");

            long streamId = initializer.httpHandler().sendRequest(request);
            initializer.httpHandler().awaitStreamResponse(streamId, 10, TimeUnit.SECONDS);
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}