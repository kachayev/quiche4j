package io.quiche4j;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class Quiche {

    // The maximum length of a connection ID.
    public static final int MAX_CONN_ID_LEN = 20;

    public static final String CONN_ID_SEED_ALGO = "HMACSHA256";

    public static long ERROR_CODE_DONE = -1L;
    public static long ERROR_CODE_H3_STREAM_BLOCKED = -13L;
    public static long SUCCESS_CODE = 0L;

    // Supported QUIC versions.
    //
    // Note that the older ones might not be fully supported.
    public static final int PROTOCOL_VERSION_DRAFT27 = 0xff00_001b;
    public static final int PROTOCOL_VERSION_DRAFT28 = 0xff00_001c;
    public static final int PROTOCOL_VERSION_DRAFT29 = 0xff00_001d;

    // The current QUIC wire version.
    public static final int PROTOCOL_VERSION = PROTOCOL_VERSION_DRAFT29;

    public static final byte[] H3_APPLICATION_PROTOCOL = "\u0005h3-29\u0005h3-28\u0005h3-27".getBytes();

    /**
     * The stream's side to shutdown.
     *
     * This should be used when calling {@link Connection#streamShutdown}.
     */
    public enum Shutdown {
        READ(0),
        WRITE(1);

        private final int value;

        Shutdown(int value) {
            this.value = value;
        }

        public final int value() {
            return this.value;
        }
    }

    public static final byte[] newConnectionId() {
        return newConnectionId(new Random());
    }

    public static final byte[] newConnectionId(Random rnd) {
		final byte[] connId = new byte[MAX_CONN_ID_LEN];
		rnd.nextBytes(connId);
		return connId;
    }

    public static final byte[] newConnectionIdSeed() {
        try {
            final SecretKey key = KeyGenerator.getInstance(CONN_ID_SEED_ALGO).generateKey();
            return key.getEncoded();
        } catch(NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate secret key");
        }
    }

    // xxx(okachiaev): additional API to work with key spec object instead of byte[]
    public static final byte[] signConnectionId(byte[] seed, byte[] data) {
        final SecretKeySpec keySpec = new SecretKeySpec(seed, CONN_ID_SEED_ALGO);
        Mac mac;
        try {
            mac = Mac.getInstance(CONN_ID_SEED_ALGO);
            mac.init(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to sign connection ID");
        }
        final byte[] signed =  mac.doFinal(data);
        return Arrays.copyOfRange(signed, 0, MAX_CONN_ID_LEN);
    }

    /**
     * Writes a version negotiation packet.
     *
     * The `scid` and {@code destinationConnId} parameters are the source connection ID and the
     * destination connection ID extracted from the received client's Initial
     * packet that advertises an unsupported version.
     */
    public static final int negotiateVersion(
            byte[] sourceConnId, byte[] destinationConnId, byte[] buf) {
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
     * The {@code sourceConnId} and {@code destinationConnId} parameters are the source
     * connection ID and the destination connection ID extracted from the received client's
     * Initial packet. The server's new source connection ID and {@code token}
     * is the address validation token the client needs to echo back.
     *
     * The application is responsible for generating the address validation
     * token to be sent to the client, and verifying tokens sent back by the
     * client. The generated token should include the {@code destinationConnId} parameter,
     * such that it can be later extracted from the token and passed to the
     * {@link #accept()} function as its {@code originalDestinationConnId} parameter.
     */
    public static final int retry(
            byte[] sourceConnId, byte[] destinationConnId, byte[] newSourceConnId,
            byte[] token, int version, byte[] buf) {
        return Native.quiche_retry(sourceConnId, destinationConnId, newSourceConnId, token, version, buf);
    }

    /**
     * Creates a new server-side connection.
     *
     * The {@code sourceConnId} parameter represents the server's source connection ID, while
     * the optional {@code originalDestinationConnId} parameter represents the original destination ID the
     * client sent before a stateless retry (this is only required when using
     * the {@link #retry()} function).
     */
    public static final Connection accept(byte[] scid, byte[] odcid, Config config) {
        final long ptr = Native.quiche_accept(scid, odcid, config.getPointer());
        return Connection.newInstance(ptr);
    }

    /**
     * Creates a new client-side connection.
     *
     * The {@code sourceConnId} parameter is used as the connection's source connection ID,
     * while the optional {@code serverName} parameter is used to verify the peer's
     * certificate.
     */
    public static final Connection connect(String serverName, byte[] connId, Config config) {
        final long ptr = Native.quiche_connect(serverName, connId, config.getPointer());
        return Connection.newInstance(ptr);
    }

}