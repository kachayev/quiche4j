package io.quiche4j;

public final class ConfigBuilder {

    private int version;
    private String certChainPath;
    private String privKeyPath;
    private Boolean verifyPeer;
    private Boolean grease;
    private boolean logKeys = false;
    private boolean enableEarlyData = false;
    private Boolean enableHystart;
    private Long maxIdleTimeout;
    private Long maxUdpPayloadSize;
    private Long initialMaxData;
    private Long initialMaxStreamDataBidiLocal;
    private Long initialMaxStreamDataBidiRemote;
    private Long initialMaxStreamDataUni;
    private Long initialMaxStreamsBidi;
    private Long initialMaxStreamsUni;
    private Long ackDelayExponent;
    private Long maxAckDelay;
    private Boolean disableActiveMigration;
    private byte[] protos;
    private String ccAlgorithmName;

    public ConfigBuilder(int version) {
        this.version = version;
    }

    public final Config build() {
        final long pointer = Native.quiche_config_new(version);
        final Config config = new Config(pointer);
        Native.registerCleaner(config, () -> {
            Native.quiche_config_free(pointer);
        });

        if (null != certChainPath) {
            Native.quiche_config_load_cert_chain_from_pem_file(pointer, certChainPath);
        }

        if (null != privKeyPath) {
            Native.quiche_config_load_priv_key_from_pem_file(pointer, privKeyPath);
        }

        if (null != verifyPeer) {
            Native.quiche_config_verify_peer(pointer, verifyPeer);
        } 

        if (null != this.grease) {
            Native.quiche_config_grease(pointer, this.grease);
        }

        if (this.logKeys) {
            Native.quiche_config_log_keys(pointer);
        }

        if (this.enableEarlyData) {
            Native.quiche_config_enable_early_data(pointer);
        }

        if (null != this.enableHystart) {
            Native.quiche_config_enable_hystart(pointer, this.enableHystart);
        }

        if (null != this.maxIdleTimeout) {
            Native.quiche_config_set_max_idle_timeout(pointer, this.maxIdleTimeout);
        }

        if (null != this.maxUdpPayloadSize) {
            Native.quiche_config_set_max_udp_payload_size(pointer, this.maxUdpPayloadSize);
        }

        if (null != this.initialMaxData) {
            Native.quiche_config_set_initial_max_data(pointer, this.initialMaxData);
        }

        if (null != this.initialMaxStreamDataBidiLocal) {
            Native.quiche_config_set_initial_max_stream_data_bidi_local(pointer, this.initialMaxStreamDataBidiLocal);
        }

        if (null != this.initialMaxStreamDataBidiRemote) {
            Native.quiche_config_set_initial_max_stream_data_bidi_remote(pointer, this.initialMaxStreamDataBidiRemote);
        }

        if (null != this.initialMaxStreamDataUni) {
            Native.quiche_config_set_initial_max_stream_data_uni(pointer, this.initialMaxStreamDataUni);
        }

        if (null != this.initialMaxStreamsBidi) {
            Native.quiche_config_set_initial_max_streams_bidi(pointer, this.initialMaxStreamsBidi);
        }

        if (null != this.initialMaxStreamsUni) {
            Native.quiche_config_set_initial_max_streams_uni(pointer, this.initialMaxStreamsUni);
        }

        if (null != this.ackDelayExponent) {
            Native.quiche_config_set_ack_delay_exponent(pointer, this.ackDelayExponent);
        }

        if (null != this.maxAckDelay) {
            Native.quiche_config_set_max_ack_delay(pointer, this.maxAckDelay);
        }

        if (null != this.disableActiveMigration) {
            Native.quiche_config_set_disable_active_migration(pointer, this.disableActiveMigration);
        }

        if (null!= this.protos) {
            final int protosCode = Native.quiche_config_set_application_protos(pointer, this.protos);
            if(Quiche.SUCCESS_CODE != protosCode)
                throw new IllegalArgumentException("Invalid application proto");
        }

        if (null != this.ccAlgorithmName) {
            final int ccAlgoCode = Native.quiche_config_set_cc_algorithm_name(pointer, this.ccAlgorithmName);
            if(Quiche.SUCCESS_CODE != ccAlgoCode)
                throw new IllegalArgumentException("Invalid cc algorithm name");
        }

        return config;
    }

    public final ConfigBuilder loadCertChainFromPemFile(String path) {
        this.certChainPath = path;
        return this;
    }

    public final ConfigBuilder loadPrivKeyFromPemFile(String path) {
        this.privKeyPath = path;
        return this;
    }

    public final ConfigBuilder withVerifyPeer(boolean v) {
        this.verifyPeer = v;
        return this;
    }

    public final ConfigBuilder withGrease(boolean v) {
        this.grease = v;
        return this;
    }

    public final ConfigBuilder logKeys() {
        this.logKeys = true;
        return this;
    }

    public final ConfigBuilder enableEarlyData() {
        this.enableEarlyData = true;
        return this;
    }

    public final ConfigBuilder enableHystart(boolean v) {
        this.enableHystart = v;
        return this;
    }

    public final ConfigBuilder withMaxIdleTimeout(long v) {
        this.maxIdleTimeout = v;
        return this;
    }

    public final ConfigBuilder withMaxUdpPayloadSize(long v) {
        this.maxUdpPayloadSize = v;
        return this;
    }

    public final ConfigBuilder withInitialMaxData(long v) {
        this.initialMaxData = v;
        return this;
    }

    public final ConfigBuilder withInitialMaxStreamDataBidiLocal(long v) {
        this.initialMaxStreamDataBidiLocal = v;
        return this;
    }

    public final ConfigBuilder withInitialMaxStreamDataBidiRemote(long v) {
        this.initialMaxStreamDataBidiRemote = v;
        return this;
    }

    public final ConfigBuilder withInitialMaxStreamDataUni(long v) {
        this.initialMaxStreamDataUni = v;
        return this;
    }

    public final ConfigBuilder withInitialMaxStreamsBidi(long v) {
        this.initialMaxStreamsBidi = v;
        return this;
    }

    public final ConfigBuilder withInitialMaxStreamsUni(long v) {
        this.initialMaxStreamsUni = v;
        return this;
    }
   
    public final ConfigBuilder withAckDelayExponent(long v) {
        this.ackDelayExponent = v;
        return this;
    }

    public final ConfigBuilder withMaxAckDelay(long v) {
        this.maxAckDelay = v;
        return this;
    }

    public final ConfigBuilder withDisableActiveMigration(boolean v) {
        return this;
    }

    public final ConfigBuilder withApplicationProtos(byte[] protos) {
        this.protos = protos;
        return this;
    }

    public final ConfigBuilder withCcAlgorithmName(String name) {
        this.ccAlgorithmName = name;
        return this;
    }

	public Config withAppli(byte[] http3ApplicationProtocol) {
		return null;
	}

}