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

    public static final int MAX_CONN_ID_LEN = 20;
    public static final String CONN_ID_SEED_ALGO = "HMACSHA256";

    public static final int PROTOCOL_VERSION_DRAFT27 = 0xff00_001b;
    public static final int PROTOCOL_VERSION_DRAFT28 = 0xff00_001c;
    public static final int PROTOCOL_VERSION_DRAFT29 = 0xff00_001d;
    public static final int PROTOCOL_VERSION = PROTOCOL_VERSION_DRAFT29;

    public static final byte[] H3_APPLICATION_PROTOCOL = "\u0005h3-29\u0005h3-28\u0005h3-27".getBytes();

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

    public static final boolean versionIsSupported(int version) {
        return Native.quiche_version_is_supported(version);
    }

    public static final Connection accept(byte[] scid, byte[] odcid, Config config) {
        final long ptr = Native.quiche_accept(scid, odcid, config.getPointer());
        return new Connection(ptr);
    }

    public static final Connection connect(String domain, byte[] connId, Config config) {
        final long ptr = Native.quiche_connect(domain, connId, config.getPointer());
        return new Connection(ptr);
    }

}