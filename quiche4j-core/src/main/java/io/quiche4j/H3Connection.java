package io.quiche4j;

import java.util.List;

// xxx(okachaiev): move this to h3 package? :thinking:
public final class H3Connection {
    
    private final long ptr;
    private final Connection conn;

    public final static H3Connection withTransport(Connection conn, H3Config config) {
        final long ptr = Native.quiche_h3_conn_new_with_transport(conn.getPointer(), config.getPointer());
        final H3Connection h3 = new H3Connection(ptr, conn);
        Native.CLEANER.register(h3, () -> h3.free());
        return h3;
    }

    private H3Connection(long ptr, Connection conn) {
        this.ptr = ptr;
        this.conn = conn;
    }

    protected final long getPointer() {
        return this.ptr;
    }

    private final void free() {
        Native.quiche_h3_conn_free(getPointer());
    }

    public final void sendRequest(List<H3Header> headers, boolean fin) {
        sendRequest(headers.toArray(new H3Header[0]), fin);
    }

    public final void sendRequest(H3Header[] headers, boolean fin) {
        Native.quiche_h3_send_request(getPointer(), conn.getPointer(), headers, fin);
    }

    public final int recvBody(long streamId, byte[] buf) {
        return Native.quiche_h3_recv_body(getPointer(), conn.getPointer(), streamId, buf);
    }

    // xxx(okachaiev): double check if we need an API option where H3 connection
    // get transport connection different from what was used to create a conn in
    // the first place
    public final long sendResponse(long streamId, List<H3Header> headers, boolean fin) {
        return sendResponse(streamId, headers.toArray(new H3Header[0]), fin);
    }

    public final long sendResponse(long streamId, H3Header[] headers, boolean fin) {
        return Native.quiche_h3_send_response(getPointer(), conn.getPointer(), streamId, headers, fin);
    }

    public final long sendBody(long streamId, byte[] body, boolean fin) {
        return Native.quiche_h3_send_body(getPointer(), conn.getPointer(), streamId, body, fin);
    }

    // Rust API returns poll event explicitly which works really well
    // with proper ADT support. Callbacks interface lacks causality but
    // this feels more Java-style of how to organize the code  
    public Long poll(H3PollEvent eventHandler) {
        return Native.quiche_h3_conn_poll(getPointer(), conn.getPointer(), eventHandler);
    }

}