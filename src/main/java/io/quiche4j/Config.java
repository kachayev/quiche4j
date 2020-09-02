package io.quiche4j;

public class Config {

    private final long ptr;

    public static final Config newConfig(int version) {
        final long ptr = Native.quiche_config_new(version);
        return new Config(ptr);
    }

    protected Config(long ptr) {
        this.ptr = ptr;
    }

    protected final long getPointer() {
        return this.ptr;
    }

    public final Config verityPeer(boolean v) {
        Native.quiche_config_verify_peer(getPointer(), v);
        return this;
    }

    public final Config grease(boolean v) {
        Native.quiche_config_grease(getPointer(), v);
        return this;
    }

    public final Config logKeys() {
        Native.quiche_config_log_keys(getPointer());
        return this;
    }

    public final Config enableEarlyData() {
        Native.quiche_config_enable_early_data(getPointer());
        return this;
    }

    public final Config setApplicationProtos(byte[] protos) {
        // xxx(okachaiev): if the result != 0, throw the exception
        Native.quiche_config_set_application_protos(getPointer(), protos);
        return this;
    }

    public final Config setMaxIdleTimeout(long v) {
        Native.quiche_config_set_max_idle_timeout(getPointer(), v);
        return this;
    }

    public final Config setMaxUdpPayloadSize(long v) {
        Native.quiche_config_set_max_udp_payload_size(getPointer(), v);
        return this;
    }

    public final Config setInitialMaxData(long v) {
        Native.quiche_config_set_initial_max_data(getPointer(), v);
        return this;
    }

    public final Config setInitialMaxStreamDataBidiLocal(long v) {
        Native.quiche_config_set_initial_max_stream_data_bidi_local(getPointer(), v);
        return this;
    }

    public final Config setInitialMaxStreamDataBidiRemote(long v) {
        Native.quiche_config_set_initial_max_stream_data_bidi_remote(getPointer(), v);
        return this;
    }

    public final Config setInitialMaxStreamDataUni(long v) {
        Native.quiche_config_set_initial_max_stream_data_uni(getPointer(), v);
        return this;
    }

    public final Config setInitialMaxStreamsBidi(long v) {
        Native.quiche_config_set_initial_max_streams_bidi(getPointer(), v);
        return this;
    }

    public final Config setInitialMaxStreamsUni(long v) {
        Native.quiche_config_set_initial_max_streams_uni(getPointer(), v);
        return this;
    }
   
    public final Config setAckDelayExponent(long v) {
        Native.quiche_config_set_ack_delay_exponent(getPointer(), v);
        return this;
    }

    public final Config setMaxAckDelay(long v) {
        Native.quiche_config_set_max_ack_delay(getPointer(), v);
        return this;
    }

    public final Config setDisableActiveMigration(boolean v) {
        Native.quiche_config_set_disable_active_migration(getPointer(), v);
        return this;
    }

    public final Config setCcAlgorithmName(String name) {
        // xxx(okachaiev): if the result != 0, throw the exception
        Native.quiche_config_set_cc_algorithm_name(getPointer(), name);
        return this;
    }

    public final Config enableHystart(boolean v) {
        Native.quiche_config_enable_hystart(getPointer(), v);
        return this;
    }

}