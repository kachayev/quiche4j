package io.quiche4j;

import java.util.Iterator;

import io.quiche4j.Quiche.Shutdown;

public class Connection {

    private final long ptr;

    private Connection(long ptr) {
        this.ptr = ptr;
    }

    public final long getPointer() {
        return this.ptr;
    }

    public final static Connection newInstance(long ptr) {
        final Connection conn = new Connection(ptr);
        Native.CLEANER.register(conn, () -> conn.free());
        return conn;
    }

    private final void free() {
        Native.quiche_conn_free(getPointer());
    }

    public final int send(byte[] buf) throws Quiche.Error {
        final int sent = Native.quiche_conn_send(getPointer(), buf);
        if(sent < 0 && Quiche.ERROR_CODE_DONE != sent)
            throw new Quiche.Error(sent);
        return sent;
    }

    public final int recv(byte[] buf) throws Quiche.Error {
        final int recv = Native.quiche_conn_recv(getPointer(), buf);
        if(recv < 0 && Quiche.ERROR_CODE_DONE != recv)
            throw new Quiche.Error(recv);
        return recv;
    }

    public final void onTimeout() {
        Native.quiche_conn_on_timeout(getPointer());
    }

    public final boolean isInEarlyData() {
        return Native.quiche_conn_is_in_early_data(getPointer());
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

    public int streamRecv(long streamId, byte[] buf) throws Quiche.Error {
        final int recv = Native.quiche_conn_stream_recv(getPointer(), streamId, buf);
        if(recv < 0 && Quiche.ERROR_CODE_DONE != recv)
            throw new Quiche.Error(recv);
        return recv;
    }

    public int streamSend(long streamId, byte[] buf, boolean fin) throws Quiche.Error {
        final int sent = Native.quiche_conn_stream_send(getPointer(), streamId, buf, fin);
        if(sent < 0 && Quiche.ERROR_CODE_DONE != sent)
            throw new Quiche.Error(sent);
        return sent;
    }

	public void streamShutdown(long streamId, Shutdown direction, long err) {
        Native.quiche_conn_stream_shutdown(getPointer(), streamId, direction.value(), err);
    }

    public int streamCapacity(long streamId) throws Quiche.Error {
        final int capacity = Native.quiche_conn_stream_capacity(getPointer(), streamId);
        if(capacity < 0) throw new Quiche.Error(capacity);
        return capacity;
    }

    public boolean streamFinished(long streamId) {
        return Native.quiche_conn_stream_finished(getPointer(), streamId);
    }

    public final static class StreamIter implements Iterator<Long>, Iterable<Long> {

        private final long ptr;
        private long nextId;
        private boolean hasNext;

        private final static StreamIter fromPointer(long ptr) {
            final StreamIter iter = new StreamIter(ptr);
            Native.CLEANER.register(iter, () -> iter.free());
            return iter;
        }

        private StreamIter(long ptr) {
            this.ptr = ptr;
            this.nextId = -1;
            // xxx(okachaiev): is there a way not to call iter when creating
            // the object? :thinking:
            this.reload();
        }
    
        private void reload() {
            final long nextStreamId = Native.quiche_stream_iter_next(ptr);
            // xxx(okachaiev): magical constant again :(
            if(-1 == nextStreamId) {
                this.hasNext = false;
            } else {
                this.nextId = nextStreamId;
                this.hasNext = true;
            }
        }

        public final Long next() {
            final long next = this.nextId;
            this.reload();
            return next;
        }

        public final boolean hasNext() {
            return this.hasNext;
        }

        public final Iterator<Long> iterator() {
            return this;
        }

        private final long getPointer() {
            return this.ptr;
        }

        private final void free() {
            Native.quiche_stream_iter_free(getPointer());
        }
    } 

    public StreamIter readable() {
        return StreamIter.fromPointer(Native.quiche_conn_readable(getPointer()));
    }

    public StreamIter writable() {
        return StreamIter.fromPointer(Native.quiche_conn_writable(getPointer()));
    }

}