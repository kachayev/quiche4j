package io.quiche4j;

public class Connection {

    private final long ptr;

    protected Connection(long ptr) {
        this.ptr = ptr;
    }

    protected final long getPointer() {
        return this.ptr;
    }

    public final int send(byte[] buf) {
        return Native.quiche_conn_send(getPointer(), buf);
    }

    public final int recv(byte[] buf) {
        return Native.quiche_conn_recv(getPointer(), buf);
    }

    public final void onTimeout() {
        Native.quiche_conn_on_timeout(getPointer());
    }

    public final boolean isEstablished() {
        return Native.quiche_conn_is_established(getPointer());
    }

    public final boolean isClosed() {
        return Native.quiche_conn_is_closed(getPointer());
    }

    public final int close(boolean app, long error, String reason) {
        return close(app, error, reason.getBytes());
    }

    public final int close(boolean app, long error, byte[] reason) {
        return Native.quiche_conn_close(getPointer(), app, error, reason);
    }

    public final Stats stats() {
        final Stats stats = new Stats();
        Native.quiche_conn_stats(getPointer(), stats);
        return stats;
    }

}