package io.quiche4j.examples;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quiche4j.Config;
import io.quiche4j.ConfigBuilder;
import io.quiche4j.Connection;
import io.quiche4j.http3.Http3;
import io.quiche4j.http3.Http3Config;
import io.quiche4j.http3.Http3ConfigBuilder;
import io.quiche4j.http3.Http3Connection;
import io.quiche4j.http3.Http3Header;
import io.quiche4j.http3.Http3EventListener;
import io.quiche4j.PacketHeader;
import io.quiche4j.PacketType;
import io.quiche4j.Quiche;
import io.quiche4j.Utils;

public class Http3Server {

    protected final static class PartialResponse {
        protected List<Http3Header> headers;
        protected byte[] body;
        protected long written;

        PartialResponse(List<Http3Header> headers, byte[] body, long written) {
            this.headers = headers;
            this.body = body;
            this.written = written;
        }
    }

    protected final static class Client {

        private final Connection conn;
        private Http3Connection h3Conn;
        private HashMap<Long, PartialResponse> partialResponses;
        private SocketAddress sender;

        public Client(Connection conn, SocketAddress sender) {
            this.conn = conn;
            this.sender = sender;
            this.h3Conn = null;
            this.partialResponses = new HashMap<>();
        }

        public final Connection connection() {
            return this.conn;
        }

        public final SocketAddress sender() {
            return this.sender;
        }

        public final Http3Connection http3Connection() {
            return this.h3Conn;
        }

        public final void setHttp3Connection(Http3Connection conn) {
            this.h3Conn = conn;
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
        if (0 < args.length) {
            if (args[0].contains(":")) {
                final String[] parts = args[0].split(":", 2);
                if (!parts[0].isEmpty())
                    hostname = parts[0];
                port = Integer.parseInt(parts[1]);
            } else {
                port = Integer.parseInt(args[0]);
            }
        }

        final byte[] buf = new byte[65535];
        final byte[] out = new byte[MAX_DATAGRAM_SIZE];

        final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
            .withApplicationProtos(Http3.APPLICATION_PROTOCOL)
            .withVerifyPeer(false)
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
            .enableEarlyData()
            .build();

        final DatagramSocket socket = new DatagramSocket(port, InetAddress.getByName(hostname));
        socket.setSoTimeout(100);

        final Http3Config h3Config = new Http3ConfigBuilder().build();
        final byte[] connIdSeed = Quiche.newConnectionIdSeed();
        final HashMap<String, Client> clients = new HashMap<>();
        final AtomicBoolean running = new AtomicBoolean(true);

        System.out.println(String.format("! listening on %s:%d", hostname, port));

        while (running.get()) {
            // READING
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    // TIMERS
                    for (Client client : clients.values()) {
                        client.connection().onTimeout();
                    }
                    break;
                }

                final int offset = packet.getOffset();
                final int len = packet.getLength();
                // xxx(okachaiev): can we avoid doing copy here?
                final byte[] packetBuf = Arrays.copyOfRange(packet.getData(), offset, len);

                System.out.println("> socket.recv " + len + " bytes");

                // PARSE QUIC HEADER
                final PacketHeader hdr;
                try {
                    int err[] = new int[1];
                    hdr = PacketHeader.parse(packetBuf, Quiche.MAX_CONN_ID_LEN, err);
                    System.out.println("> packet " + hdr);
                    if(hdr == null)
                        throw new Exception("Parse failed: " + err[0]);
                } catch (Exception e) {
                    System.out.println("! failed to parse headers " + e);
                    continue;
                }

                // SIGN CONN ID
                final byte[] connId = Quiche.signConnectionId(connIdSeed, hdr.destinationConnectionId());
                Client client = clients.get(Utils.asHex(hdr.destinationConnectionId()));
                if (null == client)
                    client = clients.get(Utils.asHex(connId));
                if (null == client) {
                    // CREATE CLIENT IF MISSING
                    if (PacketType.INITIAL != hdr.packetType()) {
                        System.out.println("! wrong packet type");
                        continue;
                    }

                    // NEGOTIATE VERSION
                    if (!Quiche.versionIsSupported(hdr.version())) {
                        System.out.println("> version negotiation");

                        final int negLength = Quiche.negotiateVersion(hdr.sourceConnectionId(),
                                hdr.destinationConnectionId(), out);
                        if (negLength < 0) {
                            System.out.println("! failed to negotiate version " + negLength);
                            System.exit(1);
                            return;
                        }
                        final DatagramPacket negPacket = new DatagramPacket(out, negLength, packet.getAddress(),
                                packet.getPort());
                        socket.send(negPacket);
                        continue;
                    }

                    // RETRY IF TOKEN IS EMPTY
                    if (null == hdr.token()) {
                        System.out.println("> stateless retry");

                        final byte[] token = mintToken(hdr, packet.getAddress());
                        final int retryLength = Quiche.retry(hdr.sourceConnectionId(), hdr.destinationConnectionId(),
                                connId, token, hdr.version(), out);
                        if (retryLength < 0) {
                            System.out.println("! retry failed " + retryLength);
                            System.exit(1);
                            return;
                        }

                        System.out.println("> retry length " + retryLength);

                        final DatagramPacket retryPacket = new DatagramPacket(out, retryLength, packet.getAddress(),
                                packet.getPort());
                        socket.send(retryPacket);
                        continue;
                    }

                    // VALIDATE TOKEN
                    final byte[] odcid = validateToken(packet.getAddress(), hdr.token());
                    if (null == odcid) {
                        System.out.println("! invalid address validation token");
                        continue;
                    }

                    byte[] sourceConnId = connId;
                    final byte[] destinationConnId = hdr.destinationConnectionId();
                    if (sourceConnId.length != destinationConnId.length) {
                        System.out.println("! invalid destination connection id");
                        continue;
                    }
                    sourceConnId = destinationConnId;

                    final Connection conn = Quiche.accept(sourceConnId, odcid, new InetSocketAddress(packet.getAddress(), packet.getPort()), config);

                    System.out.println("> new connection " + Utils.asHex(sourceConnId));

                    client = new Client(conn, packet.getSocketAddress());
                    clients.put(Utils.asHex(sourceConnId), client);

                    System.out.println("! # of clients: " + clients.size());
                }

                // POTENTIALLY COALESCED PACKETS
                final Connection conn = client.connection();
                final int read = conn.recv(packetBuf, (InetSocketAddress) client.sender);
                if (read < 0 && read != Quiche.ErrorCode.DONE) {
                    System.out.println("> recv failed " + read);
                    break;
                }
                if (read <= 0)
                    break;

                System.out.println("> conn.recv " + read + " bytes");
                System.out.println("> conn.established " + conn.isEstablished());

                // ESTABLISH H3 CONNECTION IF NONE
                Http3Connection h3Conn = client.http3Connection();
                if ((conn.isInEarlyData() || conn.isEstablished()) && null == h3Conn) {
                    System.out.println("> handshake done " + conn.isEstablished());
                    h3Conn = Http3Connection.withTransport(conn, h3Config);
                    client.setHttp3Connection(h3Conn);

                    System.out.println("> new H3 connection " + h3Conn);
                }

                if (null != h3Conn) {
                    // PROCESS WRITABLES
                    final Client current = client;
                    client.connection().writable().forEach(streamId -> {
                        handleWritable(current, streamId);
                    });

                    // H3 POLL
                    while (true) {
                        final long streamId = h3Conn.poll(new Http3EventListener() {
                            public void onHeaders(long streamId, List<Http3Header> headers, boolean hasBody) {
                                headers.forEach(header -> {
                                    System.out.println("< got header " + header.name() + " on " + streamId);
                                });
                                handleRequest(current, streamId, headers);
                            }

                            public void onData(long streamId) {
                                System.out.println("< got data on " + streamId);
                            }

                            public void onFinished(long streamId) {
                                System.out.println("< finished " + streamId);
                            }
                        });

                        if (streamId < 0 && streamId != Quiche.ErrorCode.DONE) {
                            System.out.println("! poll failed " + streamId);

                            // xxx(okachaiev): this should actially break from 2 loops
                            break;
                        }
                        // xxx(okachaiev): this should actially break from 2 loops
                        if (Quiche.ErrorCode.DONE == streamId)
                            break;

                        System.out.println("< poll " + streamId);
                    }
                }
            }

            // WRITES
            int len = 0;
            for (Client client : clients.values()) {
                final Connection conn = client.connection();

                while (true) {
                    len = conn.send(out);
                    if (len < 0 && len != Quiche.ErrorCode.DONE) {
                        System.out.println("! conn.send failed " + len);
                        break;
                    }
                    if (len <= 0)
                        break;
                    System.out.println("> conn.send " + len + " bytes");
                    final DatagramPacket packet = new DatagramPacket(out, len, client.sender());
                    socket.send(packet);
                }
            }

            // CLEANUP CLOSED CONNS
            for (String connId : clients.keySet()) {
                if (clients.get(connId).connection().isClosed()) {
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
     * The token includes the static string {@code "Quiche4j"} followed by the IP
     * address of the client and by the original destination connection ID generated
     * by the client.
     * 
     * Note that this function is only an example and doesn't do any cryptographic
     * authenticate of the token. *It should not be used in production system*.
     */
    public final static byte[] mintToken(PacketHeader hdr, InetAddress address) {
        final byte[] addr = address.getAddress();
        final byte[] dcid = hdr.destinationConnectionId();
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

    public final static void handleRequest(Client client, Long streamId, List<Http3Header> req) {
        System.out.println("< request " + streamId);

        final Connection conn = client.connection();
        final Http3Connection h3Conn = client.http3Connection();

        // SHUTDOWN STREAM
        conn.streamShutdown(streamId, Quiche.Shutdown.READ, 0L);

        final byte[] body = "Hello world".getBytes();
        final List<Http3Header> headers = new ArrayList<>();
        headers.add(new Http3Header(HEADER_NAME_STATUS, "200"));
        headers.add(new Http3Header(HEADER_NAME_SERVER, SERVER_NAME));
        headers.add(new Http3Header(HEADER_NAME_CONTENT_LENGTH, Integer.toString(body.length)));

        final long sent = h3Conn.sendResponse(streamId, headers, false);
        if (sent == Http3.ErrorCode.STREAM_BLOCKED) {
            // STREAM BLOCKED
            System.out.print("> stream " + streamId + " blocked");

            // STASH PARTIAL RESPONSE
            final PartialResponse part = new PartialResponse(headers, body, 0L);
            client.partialResponses.put(streamId, part);
            return;
        }

        if (sent < 0) {
            System.out.println("! h3.send response failed " + sent);
            return;
        }

        final long written = h3Conn.sendBody(streamId, body, true);
        if (written < 0) {
            System.out.println("! h3 send body failed " + written);
            return;
        }

        System.out.println("> send body " + written + " body");

        if (written < body.length) {
            // STASH PARTIAL RESPONSE
            final PartialResponse part = new PartialResponse(null, body, written);
            client.partialResponses.put(streamId, part);
        }
    }

    public final static void handleWritable(Client client, long streamId) {
        final PartialResponse resp = client.partialResponses.get(streamId);
        if (null == resp)
            return;

        final Http3Connection h3 = client.http3Connection();
        if (null != resp.headers) {
            final long sent = h3.sendResponse(streamId, resp.headers, false);
            if (sent == Http3.ErrorCode.STREAM_BLOCKED)
                return;
            if (sent < 0) {
                System.out.println("! h3.send response failed " + sent);
                return;
            }
        }

        resp.headers = null;

        final byte[] body = Arrays.copyOfRange(resp.body, (int) resp.written, resp.body.length);
        final long written = h3.sendBody(streamId, body, true);
        if (written < 0 && written != Quiche.ErrorCode.DONE) {
            System.out.println("! h3 send body failed " + written);
            return;
        }

        System.out.println("> send body " + written + " body");

        resp.written += written;
        if (resp.written < resp.body.length) {
            client.partialResponses.remove(streamId);
        }
    }

}