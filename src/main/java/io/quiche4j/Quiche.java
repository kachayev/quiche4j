package io.quiche4j;

import java.util.Random;

public final class Quiche {

    public static final int MAX_CONN_ID_LEN = 20;

    public static final int PROTOCOL_VERSION_DRAFT27 = 0xff00_001b;
    public static final int PROTOCOL_VERSION_DRAFT28 = 0xff00_001c;
    public static final int PROTOCOL_VERSION_DRAFT29 = 0xff00_001d;
    public static final int PROTOCOL_VERSION = PROTOCOL_VERSION_DRAFT29;

    public static final byte[] H3_APPLICATION_PROTOCOL = "\u0005h3-29\u0005h3-28\u0005h3-27".getBytes();

	public static final byte[] newConnectionId() {
		byte[] connId = new byte[MAX_CONN_ID_LEN];
		new Random().nextBytes(connId);
		return connId;
    }

    public static final Connection connect(String domain, byte[] connId, Config config) {
        final long ptr = Native.quiche_connect(domain, connId, config.getPointer());
        return new Connection(ptr);
    }

}