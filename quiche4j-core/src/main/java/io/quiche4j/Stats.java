package io.quiche4j;

public final class Stats {

    private int recv;
    private int sent;
    private int lost;
    private long rtt;
    private int cwnd;
    private long deliveryRate;

    protected Stats() {
        // no-op
    }

    public final int getRecv() {
        return this.recv;
    }

    protected final void setRecv(int recv) {
        this.recv = recv;
    }

    public final int getSent() {
        return this.sent;
    }

    protected final void setSent(int sent) {
        this.sent = sent;
    }

    public final int getLost() {
        return this.lost;
    }

    protected final void setLost(int lost) {
        this.lost = lost;
    }

    public final long getRtt() {
        return this.rtt;
    }

    protected final void setRtt(long rtt) {
        this.rtt = rtt;
    }

    public final int getCwnd() {
        return this.cwnd;
    }

    protected final void setCwnd(int cwnd) {
        this.cwnd = cwnd;
    }

    public final long getDeliveryRate() {
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