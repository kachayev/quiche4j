package io.quiche4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class Quiche {

    /**
     * The maximum length of a connection ID.
     */
    public static final int MAX_CONN_ID_LEN = 20;

    /**
     * The alogithm name for {@code KeyGenerator} to generate secret
     * spec to be used as a connection ID seed.
     */
    public static final String CONN_ID_SEED_ALGO = "HMACSHA256";

    /**
     * A listing of QUIC error codes.
     */
    public static final class ErrorCode {
        /**
         * No errors. All good.
         */
        public static final short SUCCESS = 0;

        /**
         * There is no more work to do.
         */
        public static final short DONE = -1;

        /**
         * The provided buffer is too short.
         */
        public static final short BUFFER_TOO_SHORT = -2;

        /**
         * The provided packet cannot be parsed because its version is unknown.
         */
        public static final short UNKNOWN_VERSION = -3;

        /**
         * The provided packet cannot be parsed because it contains an invalid
         * frame.
         */
        public static final short INVALID_FRAME = -4;

        /**
         * The provided packet cannot be parsed.
         */
        public static final short INVALID_PACKET = -5;

        /**
         * The operation cannot be completed because the connection is in an
         * invalid state.
         */
        public static final short INVALID_STATE = -6;

        /**
         * The operation cannot be completed because the stream is in an
         * invalid state.
         */
        public static final short INVALID_STREAM_STATE = -7;

        /**
         * The peer's transport params cannot be parsed.
         */
        public static final short INVALID_TRANSPORT_PARAM = -8;

        /**
         * A cryptographic operation failed.
         */
        public static final short CRYPTO_FAIL = -9;

        /**
         * The TLS handshake failed.
         */
        public static final short TLS_FAIL = -10;

        /**
         * The peer violated the local flow control limits.
         */
        public static final short FLOW_CONTROL = -11;

        /**
         * The peer violated the local stream limits.
         */
        public static final short STREAM_LIMIT = -12;

        /**
         * The specified stream was stopped by the peer.
         *
         * The error code sent as part of the `STOP_SENDING` frame is provided as
         * associated data.
         */
        public static final short STREAM_STOPPED = -13;

        /**
         * The specified stream was reset by the peer.
         *
         * The error code sent as part of the `RESET_STREAM` frame is provided as
         * associated data.
         */
        public static final short STREAM_RESET = -14;

        /**
         * The received data exceeds the stream's final size.
         */
        public static final short FINAL_SIZE = -15;

        /**
         * Error in congestion control.
         */
        public static final short CONGESTION_CONTROL = -16;
    }

    /**
     * Supported QUIC version:
     * https://tools.ietf.org/html/draft-ietf-quic-transport-27
     *
     * <p>Note that the older ones might not be fully supported.
     */
    public static final int PROTOCOL_VERSION_DRAFT27 = 0xff00_001b;

    /**
     * Supported QUIC version:
     * https://tools.ietf.org/html/draft-ietf-quic-transport-28
     *
     * <p>Note that the older ones might not be fully supported.
     */
    public static final int PROTOCOL_VERSION_DRAFT28 = 0xff00_001c;

    /**
     * Supported QUIC version:
     * https://tools.ietf.org/html/draft-ietf-quic-transport-29
     *
     * <p>Note that the older ones might not be fully supported.
     */
    public static final int PROTOCOL_VERSION_DRAFT29 = 0xff00_001d;

    /**
     * The current QUIC wire version.
     */
    public static final int PROTOCOL_VERSION = PROTOCOL_VERSION_DRAFT29;

    /**
     * The stream's side to shutdown.
     * 
     * <p>This should be used when calling {@link Connection#streamShutdown}.
     */
    public enum Shutdown {
        /**
         * Stop receiving stream data.
         */
        READ(0),
        
        /**
         * Stop sending stream data.
         */
        WRITE(1);

        private final int value;

        Shutdown(int value) {
            this.value = value;
        }

        public final int value() {
            return this.value;
        }
    }

    /**
     * Generate random connection ID.
     */
    public static final byte[] newConnectionId() {
        return newConnectionId(new Random());
    }
 
    /**
     * Generate random connection ID using given generator of
     * pseudorandom numbers.
     */
    public static final byte[] newConnectionId(Random rnd) {
        final byte[] connId = new byte[MAX_CONN_ID_LEN];
        rnd.nextBytes(connId);
        return connId;
    }

    /**
     * Generate new random connection ID seed.
     */
    public static final byte[] newConnectionIdSeed() {
        try {
            final SecretKey key = KeyGenerator.getInstance(CONN_ID_SEED_ALGO).generateKey();
            return key.getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate secret key");
        }
    }

    /**
     * Sign connection ID using given connection ID seed.
     */
    public static final byte[] signConnectionId(byte[] seed, byte[] data) {
        final SecretKeySpec keySpec = new SecretKeySpec(seed, CONN_ID_SEED_ALGO);
        Mac mac;
        try {
            mac = Mac.getInstance(CONN_ID_SEED_ALGO);
            mac.init(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to sign connection ID");
        }
        final byte[] signed = mac.doFinal(data);
        return Arrays.copyOfRange(signed, 0, MAX_CONN_ID_LEN);
    }

    /**
     * Writes a version negotiation packet.
     * 
     * <p>The {@code sourceConnId} and {@code destinationConnId} parameters are the
     * source connection ID and the destination connection ID extracted from the
     * received client's {@link PacketType.Initial} packet that advertises an unsupported version.
     */
    public static final int negotiateVersion(byte[] sourceConnId, byte[] destinationConnId, byte[] buf) {
        return Native.quiche_negotiate_version(sourceConnId, destinationConnId, buf);
    }

    /**
     * Returns {@code true} if the given protocol version is supported.
     */
    public static final boolean versionIsSupported(int version) {
        return Native.quiche_version_is_supported(version);
    }

    /**
     * Writes a stateless retry packet.
     *
     * <p>The {@code sourceConnId} and {@code destinationConnId} parameters are the
     * source connection ID and the destination connection ID extracted from the
     * received client's {@link PacketType.Initial} packet. The server's new source connection ID and
     * {@code token} is the address validation token the client needs to echo back.
     *
     * <p>The application is responsible for generating the address validation token to
     * be sent to the client, and verifying tokens sent back by the client. The
     * generated token should include the {@code destinationConnId} parameter, such
     * that it can be later extracted from the token and passed to the
     * {@link Quiche#accept} function as its {@code originalDestinationConnId}
     * parameter.
     */
    public static final int retry(byte[] sourceConnId, byte[] destinationConnId, byte[] newSourceConnId, byte[] token,
            int version, byte[] buf) {
        return Native.quiche_retry(sourceConnId, destinationConnId, newSourceConnId, token, version, buf);
    }

    /**
     * Creates a new server-side connection.
     *
     * <p>
     * The {@code sourceConnId} parameter represents the server's source connection
     * ID, while the optional {@code originalDestinationConnId} parameter represents
     * the original destination ID the client sent before a stateless retry (this is
     * only required when using the {@link Quiche#retry} function).
     * 
     * @throws ConnectionFailureException
     */
    public static final Connection accept(byte[] sourceConnId, byte[] originalDestinationConnId, InetSocketAddress fromAddr, Config config)
            throws ConnectionFailureException {
        final long ptr = Native.quiche_accept(sourceConnId, originalDestinationConnId, fromAddr.getAddress().getAddress(), fromAddr.getPort(), config.getPointer());
        if (ptr <= ErrorCode.SUCCESS) {
            throw new ConnectionFailureException(ptr);
        }
        return Connection.newInstance(ptr);
    }

    /**
     * Creates a new client-side connection.
     *
     * The {@code sourceConnId} parameter is used as the connection's source
     * connection ID, while the optional {@code serverName} parameter is used to
     * verify the peer's certificate.
     * 
     * @throws ConnectionFailureException
     */
    public static final Connection connect(String serverName, byte[] connId, InetSocketAddress addr, Config config)
            throws ConnectionFailureException {
        final long ptr = Native.quiche_connect(serverName, connId, addr.getAddress().getAddress(), addr.getPort(), config.getPointer());
        if (ptr <= ErrorCode.SUCCESS) {
            throw new ConnectionFailureException(ptr);
        }
        return Connection.newInstance(ptr);
    }

}