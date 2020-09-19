package io.quiche4j.http3;

import io.quiche4j.Native;

public final class Http3ConfigBuilder {

    public final Http3Config build() {
        final long ptr = Http3Native.quiche_h3_config_new();
        final Http3Config config = new Http3Config(ptr);
        Native.registerCleaner(config, () -> Http3Native.quiche_h3_config_free(ptr));
        return config;
    }

}
