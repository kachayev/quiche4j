package io.quiche4j;

import java.io.IOException;

/**
 * Represents an error that occured while creating {@code quiche::Connection}
 * (when performing {@link Quiche#connect} or {@link Quiche#accept}) before
 * the struct was allocated.
 */
public final class ConnectionFailureException extends IOException {

    private static final long serialVersionUID = 1928715020572926763L;

    /**
     * Connection error code, see {@link Quiche.ErrorCode}
     */
    private final long errorCode;

    public ConnectionFailureException(long errorCode) {
        super();
        this.errorCode = errorCode;
    }

    /**
     * Returns error code associated with the failure scenario
     */
    public final long errorCode() {
        return this.errorCode;
    }
    
}
