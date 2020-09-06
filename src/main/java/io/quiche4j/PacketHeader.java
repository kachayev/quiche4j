package io.quiche4j;

// xxx(okachaiev): move to "Packet" as a subclass
public final class PacketHeader {

    private PacketType packetType;
    private int version;
    private byte[] dcid;
    private byte[] scid;
    private long packetNum;
    private int packetNumLen;
    private byte[] token;
    private int[] versions;
    private boolean keyPhase;

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

    // xxx(okachaiev): avoid get* prefix for getters? :thinking: 
    public final PacketType getPacketType() {
        return this.packetType;
    }

    protected final void setVersion(int version) {
        this.version = version;
    }

    public final int getVersion() {
        return this.version;
    }

    // xxx(okachaiev): rename as getter
    protected final void setDcid(byte[] dcid) {
        this.dcid = dcid;
    }

    public final byte[] getDestinationConnectionId() {
        return this.dcid;
    }

    // xxx(okachaiev): rename as getter
    protected final void setScid(byte[] scid) {
        this.scid = scid;
    }

    public final byte[] getSourceConnectionId() {
        return this.scid;
    }

    protected final void setPacketNum(long packetNum) {
        this.packetNum = packetNum;
    }

    public final long getPacketNum() {
        return this.packetNum;
    }

    protected final void setPacketNumLen(int packetNumLen) {
        this.packetNumLen = packetNumLen;
    }

    public final int getPacketNumLen() {
        return this.packetNumLen;
    }

    protected final void setToken(byte[] token) {
        this.token = token;
    }

    public final byte[] getToken() {
        return this.token;
    }

    protected final void setVersions(int[] versions) {
        this.versions = versions;
    }

    public final int[] getVersions() {
        return this.versions;
    }

    protected final void setKeyPhase(boolean keyPhase) {
        this.keyPhase = keyPhase;
    }

    public final boolean getKeyPhase() {
        return this.keyPhase;
    }

    public final static PacketHeader parse(byte[] buf, int dcidLength) {
        final PacketHeader hdr = new PacketHeader();
        Native.quiche_header_from_slice(buf, dcidLength, hdr);
        return hdr;
    }

    public final String toString() {
        return String.format(
            "ty=%s version=%d dcid=%s scid=%s pkt_num=%d pkt_num_len=%d token=%s versions=%s",
            this.packetType, this.version, Utils.asHex(this.dcid), Utils.asHex(this.scid),
            this.packetNum, this.packetNumLen, Utils.asHex(this.token), this.versions);
    }

}