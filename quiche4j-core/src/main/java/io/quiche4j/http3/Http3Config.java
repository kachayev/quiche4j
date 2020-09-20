package io.quiche4j.http3;

/**
 * An HTTP/3 configuration.
 * 
 * <p>Maintains a pointer to a native object {@code quiche::h3::Config}.
 */
public class Http3Config {

    private final long ptr;

    protected Http3Config(long ptr) {
        this.ptr = ptr;
    }

    /**
     * Returns the pointer to a counterpart native object.
     * 
     * <p>Intended to be used only by the library code.
     */
    protected final long getPointer() {
        return this.ptr;
    }

}