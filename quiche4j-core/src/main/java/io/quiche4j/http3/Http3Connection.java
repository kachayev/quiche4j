package io.quiche4j.http3;

import java.util.List;

import io.quiche4j.Connection;
import io.quiche4j.Native;

public final class Http3Connection {

    private final long ptr;
    private final Connection conn;

    public final static Http3Connection withTransport(Connection conn, Http3Config config) {
        final long ptr = Http3Native.quiche_h3_conn_new_with_transport(conn.getPointer(), config.getPointer());
        final Http3Connection h3 = new Http3Connection(ptr, conn);
        Native.registerCleaner(h3, h3::free);
        return h3;
    }

    private Http3Connection(long ptr, Connection conn) {
        this.ptr = ptr;
        this.conn = conn;
    }

    public final long getPointer() {
        return this.ptr;
    }

    private final void free() {
        Http3Native.quiche_h3_conn_free(getPointer());
    }

    public final void sendRequest(List<Http3Header> headers, boolean fin) {
        sendRequest(headers.toArray(new Http3Header[0]), fin);
    }

    public final void sendRequest(Http3Header[] headers, boolean fin) {
        Http3Native.quiche_h3_send_request(getPointer(), conn.getPointer(), headers, fin);
    }

    public final int recvBody(long streamId, byte[] buf) {
        return Http3Native.quiche_h3_recv_body(getPointer(), conn.getPointer(), streamId, buf);
    }

    // xxx(okachaiev): double check if we need an API option where H3 connection
    // get transport connection different from what was used to create a conn in
    // the first place
    public final long sendResponse(long streamId, List<Http3Header> headers, boolean fin) {
        return sendResponse(streamId, headers.toArray(new Http3Header[0]), fin);
    }

    public final long sendResponse(long streamId, Http3Header[] headers, boolean fin) {
        return Http3Native.quiche_h3_send_response(getPointer(), conn.getPointer(), streamId, headers, fin);
    }

    public final long sendBody(long streamId, byte[] body, boolean fin) {
        return Http3Native.quiche_h3_send_body(getPointer(), conn.getPointer(), streamId, body, fin);
    }

    // Rust API returns poll event explicitly which works really well
    // with proper ADT support. Callbacks interface lacks causality but
    // this feels more Java-style of how to organize the code  
    public long poll(Http3EventListener listener) {
        return Http3Native.quiche_h3_conn_poll(getPointer(), conn.getPointer(), listener);
    }

}