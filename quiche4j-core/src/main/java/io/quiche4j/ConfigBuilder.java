package io.quiche4j;

// xxx(okachaiev): support `log_keys` directive

public final class ConfigBuilder {

    private int version;
    private String certChainPath;
    private String privKeyPath;
    private Boolean verifyPeer;
    private Boolean grease;
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

    /**
     * Creates a builder for a {@link Config} with the given protocol version.
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION).build();
     * </pre>
     */
    public ConfigBuilder(int version) {
        this.version = version;
    }

    /**
     * Configures the given certificate chain.
     *
     * <p>The content of `file` is parsed as a PEM-encoded leaf certificate,
     * followed by optional intermediate certificates.
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
     *         .loadCertChainFromPemFile("/path/to/cert.crt")
     *         .build();
     * </pre>
     */
    public final ConfigBuilder loadCertChainFromPemFile(String path) {
        this.certChainPath = path;
        return this;
    }

    /**
     * Configures the given private key.
     *
     * <p>The content of {@code file} is parsed as a PEM-encoded private key.
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
     *         .loadPrivKeyFromPemFile("/path/to/cert.key")
     *         .build();
     * </pre>
     */
    public final ConfigBuilder loadPrivKeyFromPemFile(String path) {
        this.privKeyPath = path;
        return this;
    }

    /**
     * Configures whether to verify the peer's certificate.
     *
     * <p>The default value is {@code true} for client connections,
     * and {@code false} for server ones.
     */
    public final ConfigBuilder withVerifyPeer(boolean v) {
        this.verifyPeer = v;
        return this;
    }

    /**
     * Configures whether to send {@code GREASE} values.
     *
     * <p>The default value is {@code true}.
     */
    public final ConfigBuilder withGrease(boolean v) {
        this.grease = v;
        return this;
    }

    /**
     * Enables sending or receiving early data.
     */
    public final ConfigBuilder enableEarlyData() {
        this.enableEarlyData = true;
        return this;
    }

    /**
     * Sets the {@code max_idle_timeout} transport parameter.
     *
     * <p>The default value is infinite, that is, no timeout is used.
     */
    public final ConfigBuilder withMaxIdleTimeout(long v) {
        this.maxIdleTimeout = v;
        return this;
    }

    /**
     * Sets the {@code max_udp_payload_size transport} parameter.
     *
     * <p>The default value is {@code 65527}.
     */
    public final ConfigBuilder withMaxUdpPayloadSize(long v) {
        this.maxUdpPayloadSize = v;
        return this;
    }

    /**
     * Sets the {@code initial_max_data} transport parameter.
     *
     * <p>When set to a non-zero value quiche will only allow at most {@code v}
     * bytes of incoming stream data to be buffered for the whole connection
     * (that is, data that is not yet read by the application) and will allow
     * more data to be received as the buffer is consumed by the application.
     *
     * <p>The default value is {@code 0}.
     */
    public final ConfigBuilder withInitialMaxData(long v) {
        this.initialMaxData = v;
        return this;
    }

    /**
     * Sets the {@code initial_max_stream_data_bidi_local} transport parameter.
     *
     * <p>When set to a non-zero value quiche will only allow at most {@code v} bytes
     * of incoming stream data to be buffered for each locally-initiated
     * bidirectional stream (that is, data that is not yet read by the
     * application) and will allow more data to be received as the buffer is
     * consumed by the application.
     *
     * <p>The default value is {@code 0}.
     */
    public final ConfigBuilder withInitialMaxStreamDataBidiLocal(long v) {
        this.initialMaxStreamDataBidiLocal = v;
        return this;
    }

    /**
     * Sets the {@code initial_max_stream_data_bidi_remote} transport parameter.
     *
     * <p>When set to a non-zero value quiche will only allow at most {@code v} bytes
     * of incoming stream data to be buffered for each remotely-initiated
     * bidirectional stream (that is, data that is not yet read by the
     * application) and will allow more data to be received as the buffer is
     * consumed by the application.
     *
     * <p>The default value is {@code 0}.
     */
    public final ConfigBuilder withInitialMaxStreamDataBidiRemote(long v) {
        this.initialMaxStreamDataBidiRemote = v;
        return this;
    }

    /**
     * Sets the {@code initial_max_stream_data_uni} transport parameter.
     *
     * <p>When set to a non-zero value quiche will only allow at most {@code v} bytes
     * of incoming stream data to be buffered for each unidirectional stream
     * (that is, data that is not yet read by the application) and will allow
     * more data to be received as the buffer is consumed by the application.
     *
     * <p>The default value is {@code 0}.
     */
    public final ConfigBuilder withInitialMaxStreamDataUni(long v) {
        this.initialMaxStreamDataUni = v;
        return this;
    }

    /**
     * Sets the {@code initial_max_streams_bidi} transport parameter.
     *
     * <p>When set to a non-zero value quiche will only allow {@code v} number of
     * concurrent remotely-initiated bidirectional streams to be open at any
     * given time and will increase the limit automatically as streams are
     * completed.
     *
     * <p>A bidirectional stream is considered completed when all incoming data
     * has been read by the application (up to the {@code fin} offset) or the
     * stream's read direction has been shutdown, and all outgoing data has
     * been acked by the peer (up to the {@code fin} offset) or the stream's write
     * direction has been shutdown.
     *
     * <p>The default value is {@code 0}.
     */
    public final ConfigBuilder withInitialMaxStreamsBidi(long v) {
        this.initialMaxStreamsBidi = v;
        return this;
    }

    /**
     * Sets the {@code initial_max_streams_uni} transport parameter.
     *
     * <p>When set to a non-zero value quiche will only allow {@code v} number of
     * concurrent remotely-initiated unidirectional streams to be open at any
     * given time and will increase the limit automatically as streams are
     * completed.
     *
     * <p>A unidirectional stream is considered completed when all incoming data
     * has been read by the application (up to the {@code fin} offset) or the
     * stream's read direction has been shutdown.
     *
     * <p>The default value is {@code 0}.
     */
    public final ConfigBuilder withInitialMaxStreamsUni(long v) {
        this.initialMaxStreamsUni = v;
        return this;
    }

    /**
     * Sets the {@code ack_delay_exponent} transport parameter.
     *
     * <p>The default value is {@code 3}.
     */
    public final ConfigBuilder withAckDelayExponent(long v) {
        this.ackDelayExponent = v;
        return this;
    }

    /**
     * Sets the {@code max_ack_delay} transport parameter.
     *
     * <p>The default value is {@code 25}.
     */
    public final ConfigBuilder withMaxAckDelay(long v) {
        this.maxAckDelay = v;
        return this;
    }

    /**
     * Sets the {@code disable_active_migration} transport parameter.
     *
     * <p>The default value is {@code false}.
     */
    public final ConfigBuilder withDisableActiveMigration(boolean v) {
        return this;
    }

    /**
     * Configures the list of supported application protocols.
     *
     * <p>The list of protocols `protos` must be in wire-format (i.e. a series
     * of non-empty, 8-bit length-prefixed strings).
     *
     * <p>On the client this configures the list of protocols to send to the
     * server as part of the ALPN extension.
     *
     * <p>On the server this configures the list of supported protocols to match
     * against the client-supplied list.
     *
     * <p>Applications must set a value, but no default is provided.
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
     *         .withApplicationProtos(Http3.APPLICATION_PROTOCOL)
     *         .build();
     * </pre>
     */
    public final ConfigBuilder withApplicationProtos(byte[] protos) {
        this.protos = protos;
        return this;
    }

    /**
     * Sets the congestion control algorithm used by string.
     *
     * <p>The default value is {@code reno}. On error {@link Quiche.ErrorCode#CongestionControl}
     * will be returned.
     * 
     * <p>Example:
     * <pre>
     *     final Config config = new ConfigBuilder(Quiche.PROTOCOL_VERSION)
     *         .withCcAlgorithmName("reno")
     *         .build();
     * </pre>
     */ 
    public final ConfigBuilder withCcAlgorithmName(String name) {
        this.ccAlgorithmName = name;
        return this;
    }

    /**
     * Configures whether to enable {@code HyStart++}.
     *
     * <p>The default value is {@code true}.
     */
    public final ConfigBuilder enableHystart(boolean v) {
        this.enableHystart = v;
        return this;
    }

    /**
     * Builds {@link Config} object, as effectively immutable.
     * 
     * <p>The configuration itself is a native struct. Java object only maintaince
     * a pointer to it. Cleaner runnable is registered to deallocate native struct
     * when {@link Config} is GC-ed.
     */
    public final Config build() {
        final long pointer = Native.quiche_config_new(version);
        final Config config = new Config(pointer);
        Native.registerCleaner(config, () -> {
            Native.quiche_config_free(pointer);
        });

        if (null != certChainPath) {
            if(Native.quiche_config_load_cert_chain_from_pem_file(pointer, certChainPath) != 0) {
                throw new SecurityException("Cannot load certChain: " + certChainPath);
            }
        }

        if (null != privKeyPath) {
            if(Native.quiche_config_load_priv_key_from_pem_file(pointer, privKeyPath) != 0) {
                throw new SecurityException("Cannot load privKey: " + privKeyPath);
            }
        }

        if (null != verifyPeer) {
            Native.quiche_config_verify_peer(pointer, verifyPeer);
        } 

        if (null != this.grease) {
            Native.quiche_config_grease(pointer, this.grease);
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
            if(Quiche.ErrorCode.SUCCESS != protosCode)
                throw new IllegalArgumentException("Invalid application proto");
        }

        if (null != this.ccAlgorithmName) {
            final int ccAlgoCode = Native.quiche_config_set_cc_algorithm_name(pointer, this.ccAlgorithmName);
            if(Quiche.ErrorCode.SUCCESS != ccAlgoCode)
                throw new IllegalArgumentException("Invalid cc algorithm name");
        }

        return config;
    }

}