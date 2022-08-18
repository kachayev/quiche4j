package io.quiche4j;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import io.quiche4j.Quiche.Shutdown;

/**
 * A QUIC connection.
 */
public class Connection {

    /**
     *  A pointer to a native {@code quiche::Connection} struct.
     */
    private final long ptr;

    /**
     * Processes QUIC packets received from the peer.
     * 
     * <p>On success the number of bytes processed from the input buffer is
     * returned. On error the connection will be closed by calling {@link Connection#close}
     * with the appropriate error code.
     * 
     * <p>Coalesced packets will be processed as necessary.
     * 
     * <p>Note that the contents of the input buffer {@code buf} might be modified by
     * this function due to, for example, in-place decryption.
     * 
     * <p>Example:
     * <pre>
     *     final byte[] buf = new byte[512];
     *     final DatagramSocket socket = new DatagramSocket(0);
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION).build();
     *     final byte[] scid = Quiche.newConnectionId();
     *     final Connection conn = Quiche.accept(scid, null, config);
     * 
     *     while(1) {
     *         final DatagramPacket packet = new DatagramPacket(buf, buf.length);
     *         socket.receive(packet);
     *         final byte[] out = Arrays.copyOfRange(
     *             packet.getData(), packet.getOffset(), packet.getLength());
     *         if(conn.recv(out) <= 0) break;
     *     }
     * </pre>
     */
    public final int recv(byte[] buf, InetSocketAddress fromAddr) {
        return Native.quiche_conn_recv(getPointer(), buf, fromAddr.getAddress().getAddress(), fromAddr.getPort());
    }

    /**
     * Writes a single QUIC packet to be sent to the peer.
     *
     * <p>On success the number of bytes written to the output buffer is
     * returned, or {@link Quiche.ErrorCode#DONE} if there was nothing to write.
     *
     * <p>The application should call {@link Connection#send} multiple times until {@link Quiche.ErrorCode#DONE} is
     * returned, indicating that there are no more packets to send. It is
     * recommended that {@link Connection#send} be called in the following cases:
     *
     * <ul>
     * <li>When the application receives QUIC packets from the peer (that is,
     * any time {@link Connection#recv} is also called).</li>
     *
     * <li>When the connection timer expires (that is, any time {@link Connection#onTimeout}
     * is also called).</li>
     *
     * <li>When the application sends data to the peer (for examples, any time
     * {@link Connection#streamSend} or {@link Connection#streamShutdown} are called).</li></ul>
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION).build();
     *     final byte[] scid = Quiche.newConnectionId();
     *     final Connection conn = Quiche.accept(scid, null, config);
     * 
     *     int len = 0;
     *     final byte[] buf = new byte[MAX_DATAGRAM_SIZE];
     *     final DatagramSocket socket = new DatagramSocket(0, "127.0.0.1");
     *     while(1) {
     *         len = conn.send(buf);
     *         // done with sending
     *         if (len == Quiche.ErrorCode.DONE) break;
     *         // error happened, deal with it
     *         if (len <= 0) break;
     *         final DatagramPacket packet = new DatagramPacket(buffer, len, address, port);
     *         socket.send(packet);
     *     }
     * </pre>
     */
    public final int send(byte[] buf, InetSocketAddress[] outAddr) {
        byte[] v4Addr = new byte[4];
        byte[] v6Addr = new byte[16];
        int[] port = new int[1];
        boolean[] isV4 = new boolean[1];
        int ret = Native.quiche_conn_send(getPointer(), buf, v4Addr, v6Addr, port, isV4);
        if(ret == -1 || outAddr == null)
            return ret;
        else {
            try {
                if (isV4[0]) {
                    outAddr[0] = new InetSocketAddress(Inet4Address.getByAddress(v4Addr), port[0]);
                }
                else {
                    outAddr[0] = new InetSocketAddress(Inet6Address.getByAddress(v6Addr), port[0]);
                }
                return ret;
            }
            catch(UnknownHostException e) {
                e.printStackTrace();
                return -1;
            }
        }
    }

    public final int send(byte[] buf) {
        return send(buf, null);
    }

    /**
     * Returns the amount of time until the next timeout event in nanoseconds.
     *
     * <p>Once the given duration has elapsed, the {@link Connection#onTimeout} method should
     * be called. A timeout of {@code 0L} means that the timer should be disarmed.
     */
    public final long timeoutAsNanos() {
        return Native.quiche_conn_timeout_as_nanos(getPointer());
    }

    /**
     * Returns the amount of time until the next timeout event in milliseconds.
     *
     * <p>Once the given duration has elapsed, the {@link Connection#onTimeout()} method should
     * be called. A timeout of {@code 0L} means that the timer should be disarmed.
     */
    public final long timeoutAsMillis() {
        return Native.quiche_conn_timeout_as_millis(getPointer());
    }

    /**
     * Processes a timeout event.
     *
     * <p>If no timeout has occurred it does nothing.
     */
    public final void onTimeout() {
        Native.quiche_conn_on_timeout(getPointer());
    }

    /**
     * Returns {@code true} if the connection has a pending handshake that has
     * progressed enough to send or receive early data.
     */
    public final boolean isInEarlyData() {
        return Native.quiche_conn_is_in_early_data(getPointer());
    }

    // xxx(okachaiev): support is_resumed

    /**
     * Returns {@code true} if the connection handshake is complete.
     */
    public final boolean isEstablished() {
        return Native.quiche_conn_is_established(getPointer());
    }

    /**
     * Returns {@code true} if the connection handshake is closed.
     */
    public final boolean isClosed() {
        return Native.quiche_conn_is_closed(getPointer());
    }

    /**
     * @see Connection#close(boolean, long, byte[])
     */
    public final int close(boolean app, long error, String reason) {
        return close(app, error, reason.getBytes());
    }

    /**
     * Closes the connection with the given error and reason.
     * 
     * <p>The {@code app} parameter specifies whether an application close
     * should be sent to the peer. Otherwise a normal connection close is sent.
     * 
     * <p>Returns {@link Quiche.ErrorCode#DONE} if the connection had already been closed.
     * 
     * <p>Note that the connection will not be closed immediately. An application
     * should continue calling {@link Connection#recv}, {@link Connection#send} and
     * {@link Connection#onTimeout} as normal, until the {@link Connection#isClosed}
     * method returns {@code true}.
     */
    public final int close(boolean app, long error, byte[] reason) {
        return Native.quiche_conn_close(getPointer(), app, error, reason);
    }

    /**
     * Collects and returns statistics about the connection.
     */
    public final Stats stats() {
        final Stats stats = new Stats();
        Native.quiche_conn_stats(getPointer(), stats);
        return stats;
    }

    /**
     * Reads contiguous data from a stream into the provided byte array.
     *
     * <p>The byte array must be sized by the caller and will be populated up to its
     * capacity.
     *
     * <p>On success the amount of bytes read is returned, or {@link Quiche.ErrorCode#DONE}
     * if there is no data to read.
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION).build();
     *     final byte[] scid = Quiche.newConnectionId();
     *     final Connection conn = Quiche.accept(scid, null, config);
     *
     *     final byte[] buf = new byte[512];
     *     final long streamId = 0;
     * 
     *     while(1) {
     *         final long int read = conn.streamRecv(streamId, buf);
     *         if (read == Quiche.ErrorCode.DONE) break;
     *         if (read < 0) break; // error!
     *         System.out.println("Got " + read + " bytes in a buffer");
     *     }
     * </pre>
     */
    public int streamRecv(long streamId, byte[] buf) {
        // xxx(okachaiev): support `fin` flag somehow :thinking:
        return Native.quiche_conn_stream_recv(getPointer(), streamId, buf);
    }

    /**
     * Writes data to a stream.
     *
     * <p>On success the number of bytes written is returned, or {@link Quiche.ErrorCode#DONE}
     * if no data was written (e.g. because the stream has no capacity).
     *
     * <p>Note that in order to avoid buffering an infinite amount of data in the
     * stream's send buffer, streams are only allowed to buffer outgoing data
     * up to the amount that the peer allows it to send (that is, up to the
     * stream's outgoing flow control capacity).
     *
     * <p>This means that the number of written bytes returned can be lower than
     * the length of the input buffer when the stream doesn't have enough
     * capacity for the operation to complete. The application should retry the
     * operation once the stream is reported as writable again.
     *
     * <p>Applications should call this method only after the handshake is
     * completed (whenever {@link Connection#isEstablished} returns {@code true}) or during
     * early data if enabled (whenever {@link Connection#isInEarlyData} returns {@code true}).
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION).build();
     *     final byte[] scid = Quiche.newConnectionId();
     *     final Connection conn = Quiche.accept(scid, null, config);
     * 
     *     final long streamId = 0;
     * 
     *     final int sent = conn.streamSend(streamId, "hello".getBytes(), true);
     *     if (sent < Quiche.ErrorCode.DONE) {
     *         System.out.println("Send failed");
     *     }
     * </pre>
     */
    public int streamSend(long streamId, byte[] buf, boolean fin) {
        return Native.quiche_conn_stream_send(getPointer(), streamId, buf, fin);
    }

    // xxx(okachaiev): support stream_priority

    /**
     * Shuts down reading or writing from/to the specified stream.
     *
     * <p>When the {@code direction} argument is set to {@link Quiche.Shutdown#READ}, outstanding
     * data in the stream's receive buffer is dropped, and no additional data
     * is added to it. Data received after calling this method is still
     * validated and acked but not stored, and {@link Connection#streamRecv} will not
     * return it to the application.
     *
     * <p>When the {@code direction} argument is set to {@link Quiche.Shutdown#WRITE}, outstanding
     * data in the stream's send buffer is dropped, and no additional data
     * is added to it. Data passed to {@link Connection#streamSend} after calling this
     * method will be ignored.
     */
    public void streamShutdown(long streamId, Shutdown direction, long err) {
        Native.quiche_conn_stream_shutdown(getPointer(), streamId, direction.value(), err);
    }

    /**
     * Returns the stream's send capacity in bytes.
     */
    public int streamCapacity(long streamId) {
        return Native.quiche_conn_stream_capacity(getPointer(), streamId);
    }

    /**
     * Returns {@code true} if all the data has been read from the specified stream.
     * 
     * <p>This instructs the application that all the data received from the
     * peer on the stream has been read, and there won't be anymore in the
     * future.
     * 
     * <p>Basically this returns true when the peer either set the {@code fin} flag
     * for the stream, or sent {@code RESET_STREAM}.
     */
    public boolean streamFinished(long streamId) {
        return Native.quiche_conn_stream_finished(getPointer(), streamId);
    }

    /**
     * Returns an iterator over streams that have outstanding data to read.
     *
     * <p>Note that the iterator will only include streams that were readable at
     * the time the iterator itself was created (i.e. when {@link Connection#readable} was
     * called). To account for newly readable streams, the iterator needs to
     * be created again.
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION).build();
     *     final byte[] scid = Quiche.newConnectionId();
     *     final Connection conn = Quiche.accept(scid, null, config);
     *     final byte[] buf = new byte[512];
     * 
     *     // Iterate over readable streams.
     *     conn.readable().forEach(streamId -> {
     *         // Stream is readable, read until there's no more data.
     *         final long int read = conn.streamRecv(streamId, buf);
     *         if (read == Quiche.ErrorCode.DONE) break;
     *         if (read < 0) break; // error!
     *         System.out.println("Got " + read + " bytes in a buffer");
     *     }
     * </pre>
     */
    public StreamIter readable() {
        return StreamIter.fromPointer(Native.quiche_conn_readable(getPointer()));
    }

    /**
     * Returns an iterator over streams that can be written to.
     *
     * <p>A "writable" stream is a stream that has enough flow control capacity to
     * send data to the peer. To avoid buffering an infinite amount of data,
     * streams are only allowed to buffer outgoing data up to the amount that
     * the peer allows to send.
     *
     * <p>Note that the iterator will only include streams that were writable at
     * the time the iterator itself was created (i.e. when {@link Connection#writable} was
     * called). To account for newly writable streams, the iterator needs to
     * be created again.
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION).build();
     *     final byte[] scid = Quiche.newConnectionId();
     *     final Connection conn = Quiche.accept(scid, null, config);
     * 
     *     // Iterate over writable streams.
     *     conn.writable().forEach(streamId -> {
     *         // Stream is writable, write some data.
     *         final int sent = conn.streamSend(streamId, "hello".getBytes(), false);
     *         if (sent < Quiche.ErrorCode.DONE) {
     *             System.out.println("Send failed");
     *         }
     *     }
     * </pre>
     */
    public StreamIter writable() {
        return StreamIter.fromPointer(Native.quiche_conn_writable(getPointer()));
    }

    private Connection(long ptr) {
        this.ptr = ptr;
    }

    /**
     * Returns the pointer to a counterpart native object.
     * 
     * <p>Intended to be used only by the library code.
     */
    public final long getPointer() {
        return this.ptr;
    }

    /**
     * Instantiates Java object with a given native pointer.
     * 
     * <p>Sets up cleanup procedure to make sure that native object is deallocated when Java object is GC-ed. 
     * 
     * <p>Intended to be used only by the library code.
     */
    protected final static Connection newInstance(long ptr) {
        final Connection conn = new Connection(ptr);
        Native.registerCleaner(conn, conn::free);
        return conn;
    }

    /**
     * Deallocates a native struct.
     */
    private final void free() {
        Native.quiche_conn_free(getPointer());
    }

    /**
     * An iterator over QUIC streams.
     * 
     * <p>Maintains a pointer to a JNI object {@code quiche::stream::StreamIter}.
     */
    public final static class StreamIter implements Iterator<Long>, Iterable<Long> {

        private final long ptr;
        private long nextId;
        private boolean hasNext;

        /**
         * Instantiates Java object with a given native pointer.
         * 
         * <p>Sets up cleanup procedure to make sure that native object is deallocated when Java object is GC-ed. 
         */
        private final static StreamIter fromPointer(long ptr) {
            final StreamIter iter = new StreamIter(ptr);
            Native.registerCleaner(iter, iter::free);
            return iter;
        }

        private StreamIter(long ptr) {
            this.ptr = ptr;
            this.nextId = Quiche.ErrorCode.DONE;
            // xxx(okachaiev): is there a way not to call iter when creating
            // the object? :thinking:
            this.reload();
        }

        private void reload() {
            final long nextStreamId = Native.quiche_stream_iter_next(ptr);
            if (Quiche.ErrorCode.DONE == nextStreamId) {
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

        /**
         * Returns the pointer to a counterpart native object.
         *
         * <p>Intended to be used only by the library code.
         */
        private final long getPointer() {
            return this.ptr;
        }

        /**
         * Deallocates native object.
         */
        private final void free() {
            Native.quiche_stream_iter_free(getPointer());
        }
    }

}