package io.quiche4j.http3;

import io.quiche4j.Native;

// xxx(okachaiev): support Http3 settings:
// * `max_header_list_size`
// * `qpack_max_table_capacity`
// * `qpack_blocked_streams`
public final class Http3ConfigBuilder {

    /**
     * Creates a new {@link Http3Config} object with default settings.
     * 
     * <p>The configuration itself is a native struct. Java object only maintaince
     * a pointer to it. Cleaner runnable is registered to deallocate native struct
     * when {@link Config} is GC-ed.
     */
    public final Http3Config build() {
        final long ptr = Http3Native.quiche_h3_config_new();
        final Http3Config config = new Http3Config(ptr);
        Native.registerCleaner(config, () -> Http3Native.quiche_h3_config_free(ptr));
        return config;
    }

}
