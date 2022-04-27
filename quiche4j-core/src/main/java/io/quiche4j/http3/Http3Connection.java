package io.quiche4j.http3;

import java.util.List;

import io.quiche4j.Connection;
import io.quiche4j.Native;

/**
 * An HTTP/3 connection.
 * 
 * <p>Maintains a pointer to a native object {@code quiche::h3::Connection}.
 */
public final class Http3Connection {

    private final long ptr;
    private final Connection conn;

    /**
     * Java object to carry around pointer to a native struct.
     */
    private Http3Connection(long ptr, Connection conn) {
        this.ptr = ptr;
        this.conn = conn;
    }

    /**
     * Creates a new HTTP/3 connection using the provided QUIC connection.
     *
     * <p>This will also initiate the HTTP/3 handshake with the peer by opening
     * all control streams (including QPACK) and sending the local settings.
     */
    public final static Http3Connection withTransport(Connection conn, Http3Config config) {
        final long ptr = Http3Native.quiche_h3_conn_new_with_transport(conn.getPointer(), config.getPointer());
        final Http3Connection h3 = new Http3Connection(ptr, conn);
        Native.registerCleaner(h3, h3::free);
        return h3;
    }

    /**
     * @see sendRequest(Http3Header[], boolean)
     */
    public final long sendRequest(List<Http3Header> headers, boolean fin) {
        return sendRequest(headers.toArray(new Http3Header[0]), fin);
    }

    /**
     * Sends an HTTP/3 request.
     *
     * <p>The request is encoded from the provided list of headers without a
     * body, and sent on a newly allocated stream. To include a body,
     * set {@code fin} as {@code false} and subsequently call {@link #sendBody} with the
     * same {@code conn} and the {@code streamId} returned from this method.
     *
     * <p>On success the newly allocated stream ID is returned.
     *
     * <p>The {@link Http3.ErrorCode#STREAM_BLOCKED} error is returned when the underlying
     * QUIC stream doesn't have enough capacity for the operation to complete. When this
     * happens the application should retry the operation once the stream is reported
     * as writable again.
     */
    public final long sendRequest(Http3Header[] headers, boolean fin) {
        return Http3Native.quiche_h3_send_request(getPointer(), conn.getPointer(), headers, fin);
    }

    /**
     * Reads request or response body data into the provided buffer.
     *
     * <p>Applications should call this method whenever the {@link #poll} method
     * executes a {@link Http3EventListener#onData} callback.
     *
     * <p>On success the amount of bytes read is returned, or {@link io.quiche4j.Quiche.ErrorCode#DONE}
     * if there is no data to read.
     */
    public final int recvBody(long streamId, byte[] buf) {
        return Http3Native.quiche_h3_recv_body(getPointer(), conn.getPointer(), streamId, buf);
    }

    /**
     * @see sendResponse(long, Http3Header[], boolean)
     */
    public final long sendResponse(long streamId, List<Http3Header> headers, boolean fin) {
        return sendResponse(streamId, headers.toArray(new Http3Header[0]), fin);
    }

    /**
     * Sends an HTTP/3 response on the specified stream with default priority.
     *
     * <p>This method sends the provided {@code headers} without a body. To include a
     * body, set {@code fin} as {@code false} and subsequently call {@link #sendBody} with
     * the same {@code conn} and {@code streamId}.
     *
     * <p>The {@link Http3.ErrorCode#STREAM_BLOCKED} error is returned when the underlying
     * QUIC stream doesn't have enough capacity for the operation to complete. When this
     * happens the application should retry the operation once the stream is reported
     * as writable again.
     */
    public final long sendResponse(long streamId, Http3Header[] headers, boolean fin) {
        return Http3Native.quiche_h3_send_response(getPointer(), conn.getPointer(), streamId, headers, fin);
    }

    /**
     * Sends an HTTP/3 body chunk on the given stream.
     *
     * <p>On success the number of bytes written is returned, or {@link io.quiche4j.Quiche.ErrorCode#DONE}
     * if no bytes could be written (e.g. because the stream is blocked).
     *
     * <p>Note that the number of written bytes returned can be lower than the
     * length of the input buffer when the underlying QUIC stream doesn't have
     * enough capacity for the operation to complete.
     *
     * <p>When a partial write happens (including when {@link io.quiche4j.Quiche.ErrorCode#DONE} is returned)
     * the application should retry the operation once the stream is reported as
     * writable again.
     */
    public final long sendBody(long streamId, byte[] body, boolean fin) {
        return Http3Native.quiche_h3_send_body(getPointer(), conn.getPointer(), streamId, body, fin);
    }

    /**
     * Processes HTTP/3 data received from the peer.
     *
     * <p>On success it executes corresponding listener method (at most single) and
     * returns the event's source stream ID. The stream ID can be used when calling
     * {@link #sendResponse} and {@link sendBody} when responding to incoming requests.
     *
     * <p>On error the connection will be closed by calling {@link Connection#close} with
     * the appropriate error code.
     *
     * <p>Observation. Rust API returns poll event explicitly which works really
     * well with proper ADT support. Listener interface with multiple callbacks
     * feels way more natural for Java code.
     */
    public long poll(Http3EventListener listener) {
        return Http3Native.quiche_h3_conn_poll(getPointer(), conn.getPointer(), listener);
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
     * Deallocates a native struct.
     */
    private final void free() {
        Http3Native.quiche_h3_conn_free(getPointer());
    }
}