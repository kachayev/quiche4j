package io.quiche4j;

public final class Native {

	public static interface H3PollEvent {
		void onHeader(long streamId, String name, String value);
		void onData(long streamId);
		void onFinished(long streamId);
	}

	public static interface Header {
		String getName();
		String getValue();
	}

	// CONFIG

	public final static native long quiche_config_new(int version);

	public final static native void quiche_config_load_cert_chain_from_pem_file(long config_prt, String path);

	public final static native void quiche_config_load_priv_key_from_pem_file(long config_ptr, String path);

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

	public final static native long quiche_accept(byte[] scid, byte[] odcid, long config_ptr);

	public final static native long quiche_connect(String server_name, byte[] scid, long config_ptr);

	public final static native boolean quiche_version_is_supported(int version);

	public final static native int quiche_conn_recv(long conn_ptr, byte[] buf);

	public final static native int quiche_conn_send(long conn_ptr, byte[] buf);

	public final static native int quiche_conn_close(long conn_ptr, boolean app, long err, byte[] reason);

	public final static native void quiche_conn_on_timeout(long conn_ptr);

	public final static native boolean quiche_conn_is_established(long conn_ptr);

	public final static native boolean quiche_conn_is_in_early_data(long conn_ptr);

	public final static native boolean quiche_conn_is_closed(long conn_ptr);

	public final static native void quiche_conn_stats(long conn_ptr, Stats holder);

	public final static native void quiche_conn_free(long conn_ptr);

	// STREAMS

	public final static native void quiche_conn_stream_shutdown(long conn_ptr, long stream_id, int direction, long err);

	public final static native long quiche_conn_readable(long conn_ptr);

	public final static native long quiche_conn_writable(long conn_ptr);

	public final static native long quiche_stream_iter_next(long stream_iter_ptr);

	// public final static native void quiche_stream_iter_free(long stream_iter_ptr);

	// HTTP3

	public final static native long quiche_h3_config_new();

	public final static native void quiche_h3_config_free(long h3_config_ptr);

	public final static native long quiche_h3_conn_new_with_transport(long conn_ptr, long h3_config_ptr);

	public final static native long quiche_h3_send_request(long h3_conn_ptr, long conn_ptr, Object[] headers, boolean fin);

	public final static native int quiche_h3_recv_body(long h3_conn_ptr, long conn_ptr, long stream_id, byte[] buf);

	public final static native int quiche_h3_send_response(long h3_conn_ptr, long conn_ptr, long stream_id, Object[] headers, boolean fin);

	public final static native long quiche_h3_send_body(long h3_conn_ptr, long conn_ptr, long stream_id, byte[] body, boolean fin);

	public final static native long quiche_h3_conn_poll(long h3_conn_ptr, long conn_ptr, H3PollEvent handler);

	// PACKET

	public final static native void quiche_header_from_slice(byte[] buf, int dcid_len, PacketHeader holder);

	static {
		System.loadLibrary("quiche_jni");
	}
}
