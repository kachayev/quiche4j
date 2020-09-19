package io.quiche4j.http3;

import io.quiche4j.Native;

public class Http3Config {

    private final long ptr;

    public static final Http3Config newInstance() {
        final long ptr = Http3Native.quiche_h3_config_new();
        final Http3Config config = new Http3Config(ptr);
        Native.registerCleaner(config, config::free);
        return config;
    }

    private Http3Config(long ptr) {
        this.ptr = ptr;
    }

    protected final long getPointer() {
        return this.ptr;
    }

    protected final void free() {
        Http3Native.quiche_h3_config_free(getPointer());
    }
}