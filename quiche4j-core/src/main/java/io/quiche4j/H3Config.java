package io.quiche4j;

public class H3Config {

    private final long ptr;

    public static final H3Config newInstance() {
        final long ptr = Native.quiche_h3_config_new();
        final H3Config config = new H3Config(ptr);
        Native.CLEANER.register(config, config::free);
        return config;
    }

    private H3Config(long ptr) {
        this.ptr = ptr;
    }

    protected final long getPointer() {
        return this.ptr;
    }

    protected final void free() {
        Native.quiche_h3_config_free(getPointer());
    }
}