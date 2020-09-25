package io.quiche4j;

/**
 * Statistics about the connection.
 *
 * A connections's statistics can be collected using the {@link Connection#stats()} method.
 */
public final class Stats {

    /**
     * The number of QUIC packets received on this connection.
     */
    private int recv;

    /**
     * The number of QUIC packets sent on this connection.    
     */
    private int sent;

    /**
     * The number of QUIC packets that were lost.    
     */
    private int lost;

    /**
     * The estimated round-trip time of the connection.    
     */
    private long rtt;

    /**
     * The size of the connection's congestion window in bytes.    
     */
    private int cwnd;

    /**
     * The estimated data delivery rate in bytes/s.
     */
    private long deliveryRate;

    /**
     * The constructor is executed from JNI code only.
     */
    Stats() {
        // no-op
    }

    public final int recv() {
        return this.recv;
    }

    protected final void setRecv(int recv) {
        this.recv = recv;
    }

    public final int sent() {
        return this.sent;
    }

    protected final void setSent(int sent) {
        this.sent = sent;
    }

    public final int lost() {
        return this.lost;
    }

    protected final void setLost(int lost) {
        this.lost = lost;
    }

    public final long rtt() {
        return this.rtt;
    }

    protected final void setRtt(long rtt) {
        this.rtt = rtt;
    }

    public final int cwnd() {
        return this.cwnd;
    }

    protected final void setCwnd(int cwnd) {
        this.cwnd = cwnd;
    }

    public final long deliveryRate() {
        return this.deliveryRate;
    }

    protected final void setDeliveryRate(long deliveryRate) {
        this.deliveryRate = deliveryRate;
    }

    public final String toString() {
        return String.format("recv=%d sent=%d lost=%d rtt=%d cwnd=%d delivery_rate=%d",
            this.recv, this.sent, this.lost, this.rtt, this.cwnd, this.deliveryRate);
    }

}