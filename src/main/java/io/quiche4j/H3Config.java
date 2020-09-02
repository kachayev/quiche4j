package io.quiche4j;

public class H3Config {

    private final long ptr;

    public static final H3Config newConfig() {
        final long ptr = Native.quiche_h3_config_new();
        return new H3Config(ptr);
    }

    protected H3Config(long ptr) {
        this.ptr = ptr;
    }

    protected final long getPointer() {
        return this.ptr;
    }

}