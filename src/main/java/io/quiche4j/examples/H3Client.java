package io.quiche4j.examples;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quiche4j.Config;
import io.quiche4j.Connection;
import io.quiche4j.H3Config;
import io.quiche4j.H3Connection;
import io.quiche4j.H3PollEvent;
import io.quiche4j.H3Header;
import io.quiche4j.Quiche;

public class H3Client {

    public static final int MAX_DATAGRAM_SIZE = 1350;

    public static final String CLIENT_NAME = "Quiche4j";

    public static void main(String[] args) throws UnknownHostException, IOException {
        if(0 == args.length) {
            System.out.println("Usage: ./h3-client.sh <URL>");
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

        final Config config = Config.newInstance(Quiche.PROTOCOL_VERSION);

        try {
            config.setApplicationProtos(Quiche.H3_APPLICATION_PROTOCOL);
        } catch (Quiche.Error e) {
            System.out.println("! wrong protocol " + e.getErrorCode());
            System.exit(1);
            return;
        }

        // CAUTION: this should not be set to `false` in production
        config.verityPeer(false);
        config.setMaxIdleTimeout(5000);
        config.setMaxUdpPayloadSize(MAX_DATAGRAM_SIZE);
        config.setInitialMaxData(10_000_000);
        config.setInitialMaxStreamDataBidiLocal(1_000_000);
        config.setInitialMaxStreamDataBidiRemote(1_000_000);
        config.setInitialMaxStreamDataUni(1_000_000);
        config.setInitialMaxStreamsBidi(100);
        config.setInitialMaxStreamsUni(100);
        config.setDisableActiveMigration(true);

		final byte[] connId = Quiche.newConnectionId();
        final Connection conn = Quiche.connect(uri.getHost(), connId, config);

        int len = 0;
        final byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        try {
            len = conn.send(buffer);
        } catch (Quiche.Error e) {
            System.out.println("! handshake init problem " + e.getErrorCode());
            System.exit(1);
            return;
        }
		System.out.println("> handshake size: " + len);

		final DatagramPacket handshakePacket = new DatagramPacket(buffer, len, address, port);
        final DatagramSocket socket = new DatagramSocket(10002);
        socket.setSoTimeout(2_000);
		socket.send(handshakePacket);

        Long streamId = null;
        final AtomicBoolean reading = new AtomicBoolean(true);
        final H3Config h3Config = H3Config.newInstance();
        DatagramPacket packet; 
        H3Connection h3Conn = null;

        while(!conn.isClosed()) {
            // READING LOOP
            while(reading.get()) {
                packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    final int recvBytes = packet.getLength();
                    System.out.println("> socket.recieve " + recvBytes + " bytes");
                    // xxx(okachaiev): if we extend `recv` API to with optional buf len,
                    // we could avoid Arrays.copy here
                    final int read = conn.recv(Arrays.copyOfRange(packet.getData(), 0, recvBytes));
                    System.out.println("> recv " + read + " bytes");
                } catch (SocketTimeoutException e) {
                    conn.onTimeout();
                    reading.set(false);
                } catch (Quiche.Error e) {
                    System.out.println("> recv failed " + e.getErrorCode());

                    reading.set(false);
                }

                // POLL
                if(null != h3Conn) {
                    try {
                        final H3Connection h3c = h3Conn;
                        streamId = h3Conn.poll(new H3PollEvent() {
                            public void onHeader(long _streamId, String name, String value) {
                                System.out.println(name + ": " + value);
                            }

                            public void onData(long streamId) {
                                try {
                                    final int bodyLength = h3c.recvBody(streamId, buffer);
                                    System.out.println("< got body " + bodyLength + " bytes for " + streamId);
                                    final byte[] body = Arrays.copyOfRange(buffer, 0, bodyLength);
                                    System.out.println(new String(body, StandardCharsets.UTF_8));
                                } catch (Quiche.Error e) {
                                    System.out.println("> recv body failed " + e.getErrorCode());
                                }
                            }

                            public void onFinished(long streamId) {
                                System.out.println("> response finished");
                                System.out.println("> close code " + conn.close(true, 0x00, "kthxbye"));
                                reading.set(false);
                            }
                        });
                    } catch (Quiche.Error e) {
                        System.out.println("> poll failed " + e.getErrorCode());
                        reading.set(false);
                        break;
                    }

                    if(null == streamId) reading.set(false);
                }
            }

            if(conn.isClosed()) {
                System.out.println("! conn is closed " + conn.stats());

                socket.close();
                System.exit(1);
                return;
            }

            if(conn.isEstablished() && null == h3Conn) {
                h3Conn = H3Connection.withTransport(conn, h3Config);
                List<H3Header> req = new ArrayList<H3Header>();
                req.add(new H3Header(":method", "GET"));
                req.add(new H3Header(":scheme", uri.getScheme()));
                req.add(new H3Header(":authority", uri.getAuthority()));
                req.add(new H3Header(":path", uri.getPath()));
                req.add(new H3Header("user-agent", CLIENT_NAME));
                req.add(new H3Header("content-length", "0"));
                h3Conn.sendRequest(req, true);
            }

            // WRITING LOOP
            while(true) {
                try {
                    len = conn.send(buffer);
                } catch (Quiche.Error e) {
                    System.out.println("! conn.send failed " + e.getErrorCode());
                    break;
                }
                if (len <= 0) break;
                System.out.println("> h3.send "+ len + " bytes");
                packet = new DatagramPacket(buffer, len, address, port);
                socket.send(packet);
            }

            if(conn.isClosed()) {
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
