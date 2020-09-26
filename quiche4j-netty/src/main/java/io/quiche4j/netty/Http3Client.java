package io.quiche4j.netty;

import java.util.List;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;

public class Http3Client {

    public interface Http3Connection {
    }

    public final static class DefaultHttp3Connection implements Http3Connection {

        // config
        // connection state
        // list of active streams
        // flow control
        // frames encoder/decoder

        private final boolean server;

        private DefaultHttp3Connection(boolean server) {
            this.server = server;
        }

        public final static DefaultHttp3Connection forClient() {
            return new DefaultHttp3Connection(false);
        }

        public final static DefaultHttp3Connection forServer() {
            return new DefaultHttp3Connection(true);
        }

    }

    public final static class Http3FrameWriter {

    }

    public final static class Http3FrameReader {

    }

    public interface Http3ConnectionEncoder {
    }

    public interface Http3ConnectionDecoder {
    }

    public final static class DefaultHttp3ConnectionEncoder implements Http3ConnectionEncoder {
        public DefaultHttp3ConnectionEncoder(Http3Connection connection, Http3FrameWriter writer) {
            // no-op
        }
    }

    public final static class DefaultHttp3ConnectionDecoder implements Http3ConnectionDecoder {
        public DefaultHttp3ConnectionDecoder(Http3Connection connection, Http3ConnectionEncoder encoder,
                Http3FrameReader reader) {
            // no-op
        }
    }

    public final class QuicHandshakeHandler implements ChannelInboundHandler {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throw Exception {
            // send ClientHello as "CryptoFrame"
            // status -> :handshaking
            // send early data
        }

        @Override
        public void read(ChannelHandlerContext ctx, ByteArray buf) {
            // if hadnshake == done, status -> :connected
            // remote itself from the pipelien
        }
    }

    public final class Http3ConnectionHandler extends ByteToMessageDecoder {

        private final Http3Connection connection;

        Http3ConnectionHandler(Http3Connection connection) {
            this.connection = connection;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            // TODO Auto-generated method stub
        }

        // on active install handshaker
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // generate source connection id and destination connection id
            // generate initial keys
            // start handshake ...
            ctx.pipeline().addLast(new QuicHandshakeHandler());
        }

        // handshaker on active to send handshake

        // proxy read and writes through flow controller

    }

    public final class Http3Initializer extends ChannelInitializer<SocketChannel> {
        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            final Http3Connection conn = DefaultHttp3Connection.forClient();
            ch.pipeline().addLast(new Http3ConnectionHandler(conn));
        }
    }

    public static void main(String[] args) {
        final EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            final Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.remoteAddress("quic.tech", 8443);
            b.handler(new Http3Initializer());
    
            final Channel ch = b.connect().syncUninterruptibly().channel();
        } finally {
            workerGroup.shutdown();
        }
    }
}
