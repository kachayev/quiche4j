package io.quiche4j;

/**
 * QUIC packet type.
 */
public enum PacketType {
    /**
     * Initial packet.
     */
    INITIAL,

    /**
     * Retry packet.
     */
    RETRY,

    /**
     * Handshake packet.
     */
    HANDSHAKE,

    /**
     * 0-RTT packet.
     */
    ZERO_RTT,

    /**
     * 1-RTT short header packet.
     */
    SHORT,    

    /**
     * Version negotiation packet.
     */
    VERSION_NEGOTIATION,
}