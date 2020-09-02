package io.quiche4j;

public final class Native {

	// CONFIG
	
	public final static native long quiche_config_new(int version);

	public final static native void quiche_config_verify_peer(long config_ptr, boolean v);

	public final static native void quiche_config_grease(long config_ptr, boolean v);

	public final static native void quiche_config_log_keys(long config_ptr);

	public final static native void quiche_config_enable_early_data(long config_ptr);

	public final static native int quiche_config_set_application_protos(long config_ptr, byte[] protos);

	public final static native void quiche_config_set_max_idle_timeout(long config_ptr, long v);

	public final static native void quiche_config_set_max_udp_payload_size(long config_ptr, long v);

	public final static native void quiche_config_set_initial_max_data(long config_ptr, long v);

	public final static native void quiche_config_set_initial_max_stream_data_bidi_local(long config_ptr, long v);

	public final static native void quiche_config_set_initial_max_stream_data_bidi_remote(long config_ptr, long v);

	public final static native void quiche_config_set_initial_max_stream_data_uni(long config_ptr, long v);

	public final static native void quiche_config_set_initial_max_streams_bidi(long config_ptr, long v);

	public final static native void quiche_config_set_initial_max_streams_uni(long config_ptr, long v);

	public final static native void quiche_config_set_ack_delay_exponent(long config_ptr, long v);

	public final static native void quiche_config_set_max_ack_delay(long config_ptr, long v);

	public final static native void quiche_config_set_disable_active_migration(long config_ptr, boolean v);

	public final static native int quiche_config_set_cc_algorithm_name(long config_ptr, String name);

	public final static native void quiche_config_enable_hystart(long config_ptr, boolean v);

	public final static native long quiche_config_free(long config_ptr);

	// CONNECTION

	public final static native long quiche_connect(String server_name, byte[] scid, long config_ptr);

	public final static native int quiche_conn_recv(long conn_ptr, byte[] buf);

	public final static native int quiche_conn_send(long conn_ptr, byte[] buf);

	public final static native int quiche_conn_close(long conn_ptr, boolean app, long err, byte[] reason);

	public final static native void quiche_conn_on_timeout(long conn_ptr);

	public final static native boolean quiche_conn_is_established(long conn_ptr);

	public final static native boolean quiche_conn_is_closed(long conn_ptr);

	public final static native void quiche_conn_stats(long conn_ptr, Stats holder);

	public final static native void quiche_conn_free(long conn_ptr);

	// HTTP3

	public final static native long quiche_h3_config_new();

	public final static native void quiche_h3_config_free(long h3_config_ptr);

	public final static native long quiche_h3_conn_new_with_transport(long conn_ptr, long h3_config_ptr);

	public final static native long quiche_h3_send_request(long h3_conn_ptr, long conn_ptr, Object[] headers, boolean fin);

	public final static native int quiche_h3_recv_body(long h3_conn_ptr, long conn_ptr, long stream_id, byte[] buf);

	public final static native long quiche_h3_conn_poll(long h3_conn_ptr, long conn_ptr, H3PollEvent handler);

	static {
		System.loadLibrary("quiche_jni");
	}
}
