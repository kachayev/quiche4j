package io.quiche4j;

/**
 * Stores configuration shared between multiple connections.
 * 
 * <p>Maintains pointer to a native object {@code quiche::Config}.
 */
public class Config {

    private final long ptr;

    /**
     * Instantiates Java object with a given native pointer.
     * 
     * <p>Intended to be used only by the library code.
     */
    protected Config(long ptr) {
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