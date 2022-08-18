package io.quiche4j;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;

/**
 * Declaration of native JNI calls for QUIC transport.
 */
public final class Native {

	public final static String LIBRARY_NAME = "quiche_jni";

	static {
		// try to load from "java.library.path" location first
		// to allow user to overwrite the library when necessary.
		// when failed, tries to load it from /native-libs/ folder in the JAR
		try {
			System.loadLibrary(LIBRARY_NAME);
		} catch (java.lang.UnsatisfiedLinkError e) {
			NativeUtils.loadEmbeddedLibrary(LIBRARY_NAME);
		}

		quiche_init_logger();
	}

	protected final static Cleaner CLEANER = Cleaner.create(); 

	public final static Cleanable registerCleaner(Object obj, Runnable action) {
		return CLEANER.register(obj, action);
	}

	private final static native void quiche_init_logger();

	// CONFIG
	
	public final static native long quiche_config_new(int version);

	public final static native int quiche_config_load_cert_chain_from_pem_file(long config_prt, String path);

	public final static native int quiche_config_load_priv_key_from_pem_file(long config_ptr, String path);

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

	public final static native long quiche_accept(byte[] scid, byte[] odcid, byte[] from_addr, int from_port, long config_ptr);

	public final static native long quiche_connect(String server_name, byte[] scid, byte[] socket_adr, int port, long config_ptr);

	public final static native int quiche_negotiate_version(byte[] scid, byte[] dcid, byte[] buf);

	public final static native boolean quiche_version_is_supported(int version);

	public final static native int quiche_retry(
		byte[] sourceConnId, byte[] destinationConnId, byte[] newSourceConnId,
		byte[] token, int version, byte[] buf);

	public final static native int quiche_conn_recv(long conn_ptr, byte[] buf, byte[] from_addr, int from_port);

	public final static native int quiche_conn_send(long conn_ptr, byte[] buf, byte[] out_v4addr, byte[] out_v6adr, int[] out_port, boolean[] out_isv4);

	public final static native int quiche_conn_close(long conn_ptr, boolean app, long err, byte[] reason);

	public final static native long quiche_conn_timeout_as_nanos(long conn_ptr);

	public final static native long quiche_conn_timeout_as_millis(long conn_ptr);

	public final static native void quiche_conn_on_timeout(long conn_ptr);

	public final static native boolean quiche_conn_is_established(long conn_ptr);

	public final static native boolean quiche_conn_is_in_early_data(long conn_ptr);

	public final static native boolean quiche_conn_is_closed(long conn_ptr);

	public final static native void quiche_conn_stats(long conn_ptr, Stats holder);

	public final static native void quiche_conn_free(long conn_ptr);

	// STREAMS

	public final static native int quiche_conn_stream_recv(long conn_ptr, long stream_id, byte[] buf);

	public final static native int quiche_conn_stream_send(long conn_ptr, long stream_id, byte[] buf, boolean fin);

	public final static native void quiche_conn_stream_shutdown(long conn_ptr, long stream_id, int direction, long err);

	public final static native int quiche_conn_stream_capacity(long conn_ptr, long stream_id);

	public final static native boolean quiche_conn_stream_finished(long conn_ptr, long stream_id);

	public final static native long quiche_conn_readable(long conn_ptr);

	public final static native long quiche_conn_writable(long conn_ptr);

	public final static native long quiche_stream_iter_next(long stream_iter_ptr);

	public final static native void quiche_stream_iter_free(long stream_iter_ptr);

	// PACKET

	public final static native int quiche_header_from_slice(byte[] buf, int dcid_len, PacketHeader holder);
}
