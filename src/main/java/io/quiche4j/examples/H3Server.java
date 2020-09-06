package io.quiche4j.examples;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quiche4j.Config;
import io.quiche4j.Connection;
import io.quiche4j.H3Config;
import io.quiche4j.H3Connection;
import io.quiche4j.H3Header;
import io.quiche4j.H3PollEvent;
import io.quiche4j.PacketHeader;
import io.quiche4j.PacketType;
import io.quiche4j.Quiche;
import io.quiche4j.Utils;

public class H3Server {

    protected final static class PartialResponse {
        protected List<H3Header> headers;
        protected byte[] body;
        protected long written;

        PartialResponse(List<H3Header> headers, byte[] body, long written) {
            this.headers = headers;
            this.body = body;
            this.written = written;
        }
    }

    protected final static class Client {

        private final Connection conn;
        private H3Connection h3Conn;
        private HashMap<Long, PartialResponse> partialResponses;
        private InetAddress address;
        private int port;

        public Client(Connection conn) {
            this.conn = conn;
            this.h3Conn = null;
            this.partialResponses = new HashMap<>();
        }

        public final Connection getConnection() {
            return this.conn;
        }

        public final H3Connection getH3Connection() {
            return this.h3Conn;
        }

        public final void setH3Conn(H3Connection conn) {
            this.h3Conn = conn;
        }

        public final void setSource(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }

        public final InetAddress getAddress() {
            return this.address;
        }

        public final int getPort() {
            return this.port;
        }

    }

    public static final int MAX_DATAGRAM_SIZE = 1350;

    public static final String SERVER_NAME = "quiche4j";

    public static void main(String[] args) throws IOException {
        final byte[] buf = new byte[65535];
        final byte[] out = new byte[MAX_DATAGRAM_SIZE];

        final DatagramSocket socket = new DatagramSocket(4433);
        socket.setSoTimeout(1_000);

        final Config config = Config.newConfig(Quiche.PROTOCOL_VERSION);
        config.verityPeer(false);
        config.loadCertChainFromPemFile("src/main/resources/examples/cert.crt");
        config.loadPrivKeyFromPemFile("src/main/resources/examples/cert.key");
        config.setApplicationProtos(Quiche.H3_APPLICATION_PROTOCOL);
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

        final H3Config h3Config = H3Config.newConfig();
        final byte[] connIdSeed = Quiche.newConnectionIdSeed();
        final HashMap<String, Client> clients = new HashMap<>();

        final AtomicBoolean reading = new AtomicBoolean(true);
        final AtomicBoolean running = new AtomicBoolean(true);

        System.out.println("! listening on 127.0.0.1:4433");

        while(running.get()) {
            // READING
            while(reading.get()) {
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    final int len = packet.getLength();
                    // xxx(okachaiev): can we avoid doing copy here?
                    final byte[] packetBuf = Arrays.copyOfRange(packet.getData(), 0, len);

                    System.out.println("> socket.recv " + len + " bytes");

                    // PARSE QUIC HEADER
                    PacketHeader hdr;
                    try {
                        hdr = PacketHeader.parse(packetBuf, Quiche.MAX_CONN_ID_LEN);
                        System.out.println("> packet " + hdr);
                    } catch (Exception e) {
                        System.out.println("> failed to parse headers " + e);
                        continue;
                    }

                    // SIGN CONN ID
                    final byte[] connId =  Quiche.signConnectionId(connIdSeed, hdr.getDestinationConnectionId());
                    Client client = clients.get(Utils.asHex(hdr.getDestinationConnectionId()));
                    if(null == client) client = clients.get(Utils.asHex(connId));
                    if(null == client) {
                        // CREATE CLIENT IF MISSING
                        if(PacketType.INITIAL != hdr.getPacketType()) {
                            System.out.println("> wrong packet type");
                            continue;
                        }

                        // NEGOTIATE VERSION
                        if(!Quiche.versionIsSupported(hdr.getVersion())) {
                            System.out.println("> version negotiation");

                            // TBD

                            continue;
                        }

                        // RETRY IF TOKEN IS MISSING
                        // TBD

                        // VALIDATE TOKEN
                        // TBD

                        final byte[] sourceConnId = connId; // hdr.getDcid(); // ???
                        final byte[] originalDestinationId = hdr.getToken(); // ???
                        final Connection conn = Quiche.accept(sourceConnId, originalDestinationId, config);

                        System.out.println("> new connection " + Utils.asHex(connId));

                        client = new Client(conn);
                        client.setSource(packet.getAddress(), packet.getPort());
                        clients.put(Utils.asHex(sourceConnId), client);
                    }

                    // POTENTIALLY COALESCED PACKETS
                    final Connection conn = client.getConnection();
                    final int read = conn.recv(packetBuf);
                    if(-1 == read) continue;
 
                    System.out.println("> conn.recv " + read + " bytes");

                    // ESTABLISH H3 CONNECTION IF NONE
                    H3Connection h3Conn = client.getH3Connection();
                    if((conn.isInEarlyData() || conn.isEstablished()) && null == h3Conn) {
                        System.out.println("> handshake done " + conn.isEstablished());
                        try {
                            h3Conn = H3Connection.withTransport(conn, h3Config);
                            client.setH3Conn(h3Conn);

                            System.out.println("> new H3 connection " + h3Conn);
                        } catch (Exception e) {
                            System.out.println("> failed to establish H3 conn " + e);
                            continue;
                        }
                    }

                    if(null != h3Conn) {
                        // PROCESS WRITABLES
                        final Client current = client;
                        client.getConnection().writable().forEach(streamId -> {
                            handleWritable(current, streamId);
                        });

                        // H3 POLL
                        final List<H3Header> headers = new ArrayList<>();
                        final Long streamId = h3Conn.poll(new H3PollEvent() {
                            public void onHeader(long streamId, String name, String value) {
                                // xxx(okachaiev): this won't work as expected in multi-threaded
                                // environment. it feels it would be reasonable to have onHeaders
                                // API instead of callback for each header separately
                                headers.add(new H3Header(name, value));
                                System.out.println("< got header " + name + " on " + streamId);
                            }

                            public void onData(long streamId) {
                                System.out.println("< got data on " + streamId);
                            }

                            public void onFinished(long streamId) {
                                System.out.println("< finished " + streamId);
                            }
                        });

                        System.out.println("< poll " + streamId);
                        if(null == streamId) {
                            reading.set(false);
                            break;
                        }

                        if(0 < headers.size()) {
                            handleRequest(client, streamId, headers);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    reading.set(false);
                    // TIMERS
                    for(Client client: clients.values()) {
                        client.getConnection().onTimeout();
                    }
                }
            }

            for(Client client: clients.values()) {
                while(true) {
                    // WRITING
                    final int len = client.getConnection().send(out);
                    if (-1 == len || 0 == len) break;
                    System.out.println("> conn.send "+ len + " bytes");
                    final DatagramPacket packet =
                        new DatagramPacket(out, len, client.getAddress(), client.getPort());
                    socket.send(packet);
                }
            }

            // CLEANUP CLOSED CONNS
            for(String connId: clients.keySet()) {
                if(clients.get(connId).getConnection().isClosed()) {
                    System.out.println("> cleaning up " + connId);

                    clients.remove(connId);
                }
            }

            // BACK TO READING
            reading.set(true);
        }

        System.out.println("> server stopped");
        socket.close();
    }

    public final static void handleRequest(Client client, Long streamId, List<H3Header> req) {
        System.out.println("< request " + streamId);

        final Connection conn = client.getConnection();
        final H3Connection h3Conn = client.getH3Connection();

        // SHUTDOWN STREAM
        conn.streamShutdown(streamId, Quiche.Shutdown.READ, 0L);

        final byte[] body = "Hello world".getBytes();
        final List<H3Header> headers = new ArrayList<>();
        headers.add(new H3Header(":status", "200"));
        headers.add(new H3Header("server", SERVER_NAME));
        headers.add(new H3Header("content-length", Integer.toString(body.length)));

        if(!h3Conn.sendResponse(streamId, headers, false)) {
            // STREAM BLOCKED
            System.out.print("> stream " + streamId + " blocked");

            // STASH PARTIAL RESPONSE
            final PartialResponse part = new PartialResponse(headers, body, 0L);
            client.partialResponses.put(streamId, part);
            return;
        }

        final long written = h3Conn.sendBody(streamId, body, true);

        System.out.println("> send body " + written + " body");

        if(written < body.length) {
            // STASH PARTIAL RESPONSE
            final PartialResponse part = new PartialResponse(null, body, written);
            client.partialResponses.put(streamId, part);
        }
    }

    public final static void handleWritable(Client client, long streamId) {
        final PartialResponse resp = client.partialResponses.get(streamId);
        if(null == resp) return;

        final H3Connection h3 = client.getH3Connection();
        if(null != resp.headers) {
            final boolean sent = h3.sendResponse(streamId, resp.headers, false);
            if(!sent) return;
        }

        resp.headers = null;

        final byte[] body = Arrays.copyOfRange(resp.body, (int) resp.written, resp.body.length);
        final long written = h3.sendBody(streamId, body, true);

        System.out.println("> send body " + written + " body");

        resp.written += written;
        if(resp.written < resp.body.length) {
            client.partialResponses.remove(streamId);
        }
    }

}