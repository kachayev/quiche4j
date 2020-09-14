package io.quiche4j.examples;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
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

        public final void setH3Connection(H3Connection conn) {
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

    private static final int MAX_DATAGRAM_SIZE = 1350;
    private static final String SERVER_NAME = "Quiche4j";
    private static final byte[] SERVER_NAME_BYTES = SERVER_NAME.getBytes();
    private static final int SERVER_NAME_BYTES_LEN = SERVER_NAME_BYTES.length;

    private static final String HEADER_NAME_STATUS = ":status";
    private static final String HEADER_NAME_SERVER = "server";
    private static final String HEADER_NAME_CONTENT_LENGTH = "content-length";

    public static void main(String[] args) throws IOException {
        String hostname = "localhost";
        int port = 4433;
        if(0 < args.length) {
            if(args[0].contains(":")) {
                final String[] parts = args[0].split(":", 2);
                if(!parts[0].isEmpty()) hostname = parts[0];
                port = Integer.parseInt(parts[1]);
            } else {
                port = Integer.parseInt(args[0]);
            }
        }

        final byte[] buf = new byte[65535];
        final byte[] out = new byte[MAX_DATAGRAM_SIZE];

        final Config config = Config.newInstance(Quiche.PROTOCOL_VERSION);
        
        try {
            config.setApplicationProtos(Quiche.H3_APPLICATION_PROTOCOL);
        } catch (Quiche.Error e) {
            System.out.println("! wrong protocol " + e.getErrorCode());
            System.exit(1);
            return;
        }

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

        final DatagramSocket socket = new DatagramSocket(port, InetAddress.getByName(hostname));
        socket.setSoTimeout(100);

        final H3Config h3Config = H3Config.newInstance();
        final byte[] connIdSeed = Quiche.newConnectionIdSeed();
        final HashMap<String, Client> clients = new HashMap<>();
        final AtomicBoolean running = new AtomicBoolean(true);

        System.out.println(String.format("! listening on %s:%d", hostname, port));

        while(running.get()) {
            // READING
            while(true) {
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    // TIMERS
                    for(Client client: clients.values()) {
                        client.getConnection().onTimeout();
                    }
                    break;
                }

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
                    System.out.println("! failed to parse headers " + e);
                    continue;
                }

                // SIGN CONN ID
                final byte[] connId =  Quiche.signConnectionId(connIdSeed, hdr.getDestinationConnectionId());
                Client client = clients.get(Utils.asHex(hdr.getDestinationConnectionId()));
                if(null == client) client = clients.get(Utils.asHex(connId));
                if(null == client) {
                    // CREATE CLIENT IF MISSING
                    if(PacketType.INITIAL != hdr.getPacketType()) {
                        System.out.println("! wrong packet type");
                        continue;
                    }

                    // NEGOTIATE VERSION
                    if(!Quiche.versionIsSupported(hdr.getVersion())) {
                        System.out.println("> version negotiation");

                        int negLength = 0;
                        try {
                            negLength = Quiche.negotiateVersion(
                                hdr.getSourceConnectionId(), hdr.getDestinationConnectionId(), out);
                        } catch (Quiche.Error e) {
                            System.out.println("! failed to negotiate version " + e.getErrorCode());
                            System.exit(1);
                            return;
                        }
                        final DatagramPacket negPacket =
                            new DatagramPacket(out, negLength, packet.getAddress(), packet.getPort());
                        socket.send(negPacket);
                        continue;
                    }

                    // RETRY IF TOKEN IS EMPTY
                    if(null == hdr.getToken()) {
                        System.out.println("> stateless retry");

                        final byte[] token = mintToken(hdr, packet.getAddress());
                        int retryLength = 0;
                        try {
                            retryLength = Quiche.retry(
                                hdr.getSourceConnectionId(), hdr.getDestinationConnectionId(),
                                connId, token, hdr.getVersion(), out);

                            System.out.println("> retry length " + retryLength);
                        } catch (Quiche.Error e) {
                            System.out.println("! retry failed " + e.getErrorCode());
                            System.exit(1);
                            return;
                        }

                        final DatagramPacket retryPacket =
                            new DatagramPacket(out, retryLength, packet.getAddress(), packet.getPort());
                        socket.send(retryPacket);
                        continue;
                    }

                    // VALIDATE TOKEN
                    final byte[] odcid = validateToken(packet.getAddress(), hdr.getToken());
                    if(null == odcid) {
                        System.out.println("! invalid address validation token");
                        continue;
                    }

                    byte[] sourceConnId = connId;
                    final byte[] destinationConnId = hdr.getDestinationConnectionId();
                    if(sourceConnId.length != destinationConnId.length) {
                        System.out.println("! invalid destination connection id");
                        continue;
                    }
                    sourceConnId = destinationConnId;

                    final Connection conn = Quiche.accept(sourceConnId, odcid, config);

                    System.out.println("> new connection " + Utils.asHex(sourceConnId));

                    client = new Client(conn);
                    client.setSource(packet.getAddress(), packet.getPort());
                    clients.put(Utils.asHex(sourceConnId), client);

                    System.out.println("! # of clients: " + clients.size());
                }

                // POTENTIALLY COALESCED PACKETS
                final Connection conn = client.getConnection();
                int read;
                try {
                    read = conn.recv(packetBuf);
                } catch (Quiche.Error e) {
                    System.out.println("> recv failed " + e.getErrorCode());
                    break;
                }
                if(read <= 0) break;

                System.out.println("> conn.recv " + read + " bytes");
                System.out.println("> conn.established " + conn.isEstablished());

                // ESTABLISH H3 CONNECTION IF NONE
                H3Connection h3Conn = client.getH3Connection();
                if((conn.isInEarlyData() || conn.isEstablished()) && null == h3Conn) {
                    System.out.println("> handshake done " + conn.isEstablished());
                    try {
                        h3Conn = H3Connection.withTransport(conn, h3Config);
                        client.setH3Connection(h3Conn);

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
                    Long streamId = 0L;
                    while(true) {
                        try {
                            streamId = h3Conn.poll(new H3PollEvent() {
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
                        } catch (Quiche.Error e) {
                            System.out.println("! poll failed " + e.getErrorCode());

                            // xxx(okachaiev): this should actially break from 2 loops
                            break;
                        }

                        System.out.println("< poll " + streamId);
                        // xxx(okachaiev): this should actially break from 2 loops
                        if(null == streamId) break;

                        if(0 < headers.size()) {
                            handleRequest(client, streamId, headers);
                        }
                    }
                }
            }

            // WRITES
            int len = 0;
            for(Client client: clients.values()) {
                final Connection conn = client.getConnection();
                
                while(true) {
                    try {
                        len = conn.send(out);
                    } catch (Quiche.Error e) {
                        System.out.println("! conn.send failed " + e.getErrorCode());
                        break;
                    }
                    if (len <= 0) break;
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

                    System.out.println("! # of clients: " + clients.size());
                }
            }

            // BACK TO READING
        }

        System.out.println("> server stopped");
        socket.close();
    }

    /** 
     * Generate a stateless retry token.
     * 
     * The token includes the static string `"Quiche4j"` followed by the IP address
     * of the client and by the original destination connection ID generated by the
     * client.
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
        if(token.length <= 8) return null;
        if(!Arrays.equals(SERVER_NAME_BYTES, Arrays.copyOfRange(token, 0, SERVER_NAME_BYTES_LEN))) return null;
        final byte[] addr = address.getAddress();
        if(!Arrays.equals(addr, Arrays.copyOfRange(token, SERVER_NAME_BYTES_LEN, addr.length+SERVER_NAME_BYTES_LEN)))
            return null;
        return Arrays.copyOfRange(token, SERVER_NAME_BYTES_LEN + addr.length, token.length);
    }

    public final static void handleRequest(Client client, Long streamId, List<H3Header> req) {
        System.out.println("< request " + streamId);

        final Connection conn = client.getConnection();
        final H3Connection h3Conn = client.getH3Connection();

        // SHUTDOWN STREAM
        conn.streamShutdown(streamId, Quiche.Shutdown.READ, 0L);

        final byte[] body = "Hello world".getBytes();
        final List<H3Header> headers = new ArrayList<>();
        headers.add(new H3Header(HEADER_NAME_STATUS, "200"));
        headers.add(new H3Header(HEADER_NAME_SERVER, SERVER_NAME));
        headers.add(new H3Header(HEADER_NAME_CONTENT_LENGTH, Integer.toString(body.length)));

        try {
            if(!h3Conn.sendResponse(streamId, headers, false)) {
                // STREAM BLOCKED
                System.out.print("> stream " + streamId + " blocked");
    
                // STASH PARTIAL RESPONSE
                final PartialResponse part = new PartialResponse(headers, body, 0L);
                client.partialResponses.put(streamId, part);
                return;
            }
        } catch (Quiche.Error e) {
            System.out.println("! h3.send response failed " + e.getErrorCode());
            return;
        }

        long written = 0;
        try {
            written = h3Conn.sendBody(streamId, body, true);
        } catch (Quiche.Error e) {
            System.out.println("! h3 send body failed " + e.getErrorCode());
            return;
        }

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
            try {
                final boolean sent = h3.sendResponse(streamId, resp.headers, false);
                if(!sent) return;
            } catch (Quiche.Error e) {
                System.out.println("! h3.send response failed " + e.getErrorCode());
                return;
            }
        }

        resp.headers = null;

        final byte[] body = Arrays.copyOfRange(resp.body, (int) resp.written, resp.body.length);
        long written = 0;
        try {
            written = h3.sendBody(streamId, body, true);
        } catch (Quiche.Error e) {
            System.out.println("! h3 send body failed " + e.getErrorCode());
            return;
        }

        System.out.println("> send body " + written + " body");

        resp.written += written;
        if(resp.written < resp.body.length) {
            client.partialResponses.remove(streamId);
        }
    }

}