package io.quiche4j;

/**
 * A QUIC packet's header.
 */
public final class PacketHeader {

    /**
     * The type of the packet.    
     */
    private PacketType packetType;

    /**
     * The version of the packet.
     */
    private int version;

    /**
     * The destination connection ID of the packet.    
     */
    private byte[] dcid;

    /**
     * The source connection ID of the packet.
     */
    private byte[] scid;

    /**
     * The packet number. It's only meaningful after the header protection is
     * removed.    
     */
    private long packetNum;

    /**
     * The length of the packet number. It's only meaningful after the header
     * protection is removed.
     */
    private int packetNumLen;

    /**
     * The address verification token of the packet. Only present in {@link PacketType.Initial}
     * and {@link PacketType.Retry} packets.
     */
    private byte[] token;

    /**
     * The list of versions in the packet. Only present in
     * {@link PacketType.VersionNegotiation} packets.
     */
    private int[] versions;

    /**
     * The key phase bit of the packet. It's only meaningful after the header
     * protection is removed.
     */
    private boolean keyPhase;

    /**
     * Create a packet struct with default member values.
     *
     * <p>The constructure could be called only from {@link PacketHeader#parse} method.
     */
    private PacketHeader() {
        this.packetNum = 0L;
        this.packetNumLen = 0;
        this.keyPhase = false;
    }

    protected final void setPacketType(PacketType packetType) {
        this.packetType = packetType;
    }

    protected final void setPacketType(int packetType) {
        switch(packetType) {
            case 1:
                this.setPacketType(PacketType.INITIAL);
                break;
            case 2:
                this.setPacketType(PacketType.RETRY);
                break;
            case 3:
                this.setPacketType(PacketType.HANDSHAKE);
                break;
            case 4:
                this.setPacketType(PacketType.ZERO_RTT);
                break;
            case 5:
                this.setPacketType(PacketType.SHORT);
                break;
            case 6:
                this.setPacketType(PacketType.VERSION_NEGOTIATION);
                break;
        }
    }

    public final PacketType packetType() {
        return this.packetType;
    }

    protected final void setVersion(int version) {
        this.version = version;
    }

    public final int version() {
        return this.version;
    }

    protected final void setDestinationConnectionId(byte[] dcid) {
        this.dcid = dcid;
    }

    public final byte[] destinationConnectionId() {
        return this.dcid;
    }

    protected final void setSourceConnectionId(byte[] scid) {
        this.scid = scid;
    }

    public final byte[] sourceConnectionId() {
        return this.scid;
    }

    protected final void setPacketNum(long packetNum) {
        this.packetNum = packetNum;
    }

    public final long packetNum() {
        return this.packetNum;
    }

    protected final void setPacketNumLen(int packetNumLen) {
        this.packetNumLen = packetNumLen;
    }

    public final int packetNumLen() {
        return this.packetNumLen;
    }

    protected final void setToken(byte[] token) {
        this.token = token.length > 0 ? token : null;
    }

    public final byte[] token() {
        return this.token;
    }

    protected final void setVersions(int[] versions) {
        this.versions = versions;
    }

    public final int[] versions() {
        return this.versions;
    }

    protected final void setKeyPhase(boolean keyPhase) {
        this.keyPhase = keyPhase;
    }

    public final boolean keyPhase() {
        return this.keyPhase;
    }

    /**
     * Parses a QUIC packet header from the given buffer.
     * 
     * <p>The {@code dcidLength} parameter is the length of the destination connection ID,
     * required to parse short header packets.
     * 
     * <p>Example:
     * <pre>
     *     final byte[] buf = new byte[512];
     *     final DatagramSocket socket = new DatagramSocket(0);
     *     final DatagramPacket packet = new DatagramPacket(buf, buf.length);
     *     final byte[] header = Arrays.copyOfRange(
     *         packet.getData(), packet.getOffset(), packet.getLength());
     *     final PacketHeader hdr = PacketHeader::parse(header, 16)
     * </pre>
     */
    public final static PacketHeader parse(byte[] buf, int dcidLength, int errorCode[]) {
        final PacketHeader hdr = new PacketHeader();
        int err = Native.quiche_header_from_slice(buf, dcidLength, hdr);
        if(err < 0) {
            if(errorCode != null && errorCode.length > 0)
                errorCode[0] = err;
            return null;
        }
        else
            return hdr;
    }

    public final String toString() {
        return String.format(
            "ty=%s version=%d dcid=%s scid=%s pkt_num=%d pkt_num_len=%d token=%s versions=%s",
            this.packetType, this.version, Utils.asHex(this.dcid), Utils.asHex(this.scid),
            this.packetNum, this.packetNumLen, Utils.asHex(this.token), this.versions);
    }

}