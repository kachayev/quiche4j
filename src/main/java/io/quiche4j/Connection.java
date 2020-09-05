package io.quiche4j;

import java.util.ArrayList;
import java.util.Iterator;

import io.quiche4j.Quiche.Shutdown;

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

	public void streamShutdown(long streamId, Shutdown direction, long err) {
        Native.quiche_conn_stream_shutdown(getPointer(), streamId, direction.value(), err);
    }
    
    public final static class StreamIter implements Iterator<Long>, Iterable<Long> {

        private final long ptr;
        private long nextId;
        private boolean hasNext;

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
    } 

    public StreamIter readable() {
        return new StreamIter(Native.quiche_conn_readable(getPointer()));
    }

    public StreamIter writable() {
        return new StreamIter(Native.quiche_conn_writable(getPointer()));
    }

}