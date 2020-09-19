package io.quiche4j.http3;

import static io.quiche4j.Native.LIBRARY_NAME;

import io.quiche4j.NativeUtils;

public class Http3Native {

    static {
        try {
            System.loadLibrary(LIBRARY_NAME);
        } catch (java.lang.UnsatisfiedLinkError e) {
            NativeUtils.loadEmbeddedLibrary(LIBRARY_NAME);
		}
	}

	public final static native long quiche_h3_config_new();

	public final static native void quiche_h3_config_free(long h3_config_ptr);

	public final static native long quiche_h3_conn_new_with_transport(long conn_ptr, long h3_config_ptr);

	public final static native void quiche_h3_conn_free(long conn_ptr);

	public final static native long quiche_h3_send_request(long h3_conn_ptr, long conn_ptr, Http3Header[] headers, boolean fin);

	public final static native int quiche_h3_recv_body(long h3_conn_ptr, long conn_ptr, long stream_id, byte[] buf);

	public final static native int quiche_h3_send_response(long h3_conn_ptr, long conn_ptr, long stream_id, Http3Header[] headers, boolean fin);

	public final static native long quiche_h3_send_body(long h3_conn_ptr, long conn_ptr, long stream_id, byte[] body, boolean fin);

	public final static native long quiche_h3_conn_poll(long h3_conn_ptr, long conn_ptr, Http3EventListener listener);

}
