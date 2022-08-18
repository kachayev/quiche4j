package io.quiche4j.examples;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quiche4j.Config;
import io.quiche4j.ConfigBuilder;
import io.quiche4j.Connection;
import io.quiche4j.http3.Http3;
import io.quiche4j.http3.Http3Config;
import io.quiche4j.http3.Http3ConfigBuilder;
import io.quiche4j.http3.Http3Connection;
import io.quiche4j.http3.Http3EventListener;
import io.quiche4j.http3.Http3Header;
import io.quiche4j.Quiche;
import io.quiche4j.Utils;

public class Http3Client {

    public static final int MAX_DATAGRAM_SIZE = 1350;

    public static final String CLIENT_NAME = "Quiche4j";

    public static void main(String[] args) throws UnknownHostException, IOException {
        if (0 == args.length) {
            System.out.println("Usage: ./http3-client.sh <URL>");
            System.exit(1);
        }

        final String url = args[0];
        URI uri;
        try {
            uri = new URI(url);
            System.out.println("> sending request to " + uri);
        } catch (URISyntaxException e) {
            System.out.println("Failed to parse URL " + url);
            System.exit(1);
            return;
        }

        final int port = uri.getPort();
        final InetAddress address = InetAddress.getByName(uri.getHost());

        final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
            .withApplicationProtos(Http3.APPLICATION_PROTOCOL)
            // CAUTION: this should not be set to `false` in production
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

        final byte[] connId = Quiche.newConnectionId();
        final Connection conn = Quiche.connect(uri.getHost(), connId, new InetSocketAddress(address, port), config);

        int len = 0;
        final byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        len = conn.send(buffer, null);
        if (len < 0 && len != Quiche.ErrorCode.DONE) {
            System.out.println("! handshake init problem " + len);
            System.exit(1);
            return;
        }
        System.out.println("> handshake size: " + len);

        final DatagramPacket handshakePacket = new DatagramPacket(buffer, len, address, port);
        final DatagramSocket socket = new DatagramSocket(0);
        socket.setSoTimeout(200);
        socket.send(handshakePacket);

        Long streamId = null;
        final AtomicBoolean reading = new AtomicBoolean(true);
        final Http3Config h3Config = new Http3ConfigBuilder().build();
        DatagramPacket packet;
        Http3Connection h3Conn = null;

        while (!conn.isClosed()) {
            // READING LOOP
            while (reading.get()) {
                packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    final int recvBytes = packet.getLength();

                    System.out.println("> socket.recieve " + recvBytes + " bytes");

                    // xxx(okachaiev): if we extend `recv` API to deal with optional buf len,
                    // we could avoid Arrays.copy here
                    final int read = conn.recv(Arrays.copyOfRange(packet.getData(), packet.getOffset(), recvBytes), (InetSocketAddress) packet.getSocketAddress());
                    if (read < 0 && read != Quiche.ErrorCode.DONE) {
                        System.out.println("> conn.recv failed " + read);

                        reading.set(false);
                    } else {
                        System.out.println("> conn.recv " + read + " bytes");
                    }
                } catch (SocketTimeoutException e) {
                    conn.onTimeout();
                    reading.set(false);
                }

                // POLL
                if (null != h3Conn) {
                    final Http3Connection h3c = h3Conn;
                    streamId = h3c.poll(new Http3EventListener() {
                        public void onHeaders(long streamId, List<Http3Header> headers, boolean hasBody) {
                            headers.forEach(header -> {
                                System.out.println(header.name() + ": " + header.value());
                            });
                        }

                        public void onData(long streamId) {
                            final int bodyLength = h3c.recvBody(streamId, buffer);
                            if (bodyLength < 0) {
                                System.out.println("! recv body failed " + bodyLength);
                            } else {
                                System.out.println("< got body " + bodyLength + " bytes for " + streamId);
                                final byte[] body = Arrays.copyOfRange(buffer, 0, bodyLength);
                                System.out.println(new String(body, StandardCharsets.UTF_8));
                            }
                        }

                        public void onFinished(long streamId) {
                            System.out.println("> response finished");
                            System.out.println("> close code " + conn.close(true, 0x00, "kthxbye"));
                            reading.set(false);
                        }
                    });

                    if (streamId < 0 && streamId != Quiche.ErrorCode.DONE) {
                        System.out.println("> poll failed " + streamId);
                        reading.set(false);
                        break;
                    }

                    if (Quiche.ErrorCode.DONE == streamId)
                        reading.set(false);
                }
            }

            if (conn.isClosed()) {
                System.out.println("! conn is closed " + conn.stats());

                socket.close();
                System.exit(1);
                return;
            }

            if (conn.isEstablished() && null == h3Conn) {
                h3Conn = Http3Connection.withTransport(conn, h3Config);

                System.out.println("! h3 conn is established");

                List<Http3Header> req = new ArrayList<>();
                req.add(new Http3Header(":method", "GET"));
                req.add(new Http3Header(":scheme", uri.getScheme()));
                req.add(new Http3Header(":authority", uri.getAuthority()));
                req.add(new Http3Header(":path", uri.getPath()));
                req.add(new Http3Header("user-agent", CLIENT_NAME));
                req.add(new Http3Header("content-length", "0"));
                h3Conn.sendRequest(req, true);
            }

            // WRITING LOOP
            while (true) {
                len = conn.send(buffer);
                if (len < 0 && len != Quiche.ErrorCode.DONE) {
                    System.out.println("! conn.send failed " + len);
                    break;
                }
                if (len <= 0)
                    break;
                System.out.println("> conn.send " + len + " bytes");
                packet = new DatagramPacket(buffer, len, address, port);
                socket.send(packet);
            }

            if (conn.isClosed()) {
                System.out.println("! conn is closed " + conn.stats());

                socket.close();
                System.exit(1);
                return;
            }

            reading.set(true);
        }

        System.out.println("> conn is closed");
        System.out.println(conn.stats());
        socket.close();
    }
}
