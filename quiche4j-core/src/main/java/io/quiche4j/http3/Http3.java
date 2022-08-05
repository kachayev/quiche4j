package io.quiche4j.http3;

public final class Http3 {

    /**
     * List of ALPN tokens of supported HTTP/3 versions.
     *
     * <p>This can be passed directly to the {@link ConfigBuilder#withApplicationProtos}
     * method when implementing HTTP/3 applications.
     */
    public static final byte[] APPLICATION_PROTOCOL = "\u0002h3\u0005h3-29\u0005h3-28\u0005h3-27".getBytes();

    /**
     * A listing of HTTP3 error codes.
     *
     *  [NOTICE]
     *    TRANSPORT_ERROR has two states in error code. Upper byte is error code on transport (defined in Quiche.ErrorCode),
     *    and lower byte is HTTP3 error code. So you must divide it into two error codes by h3Error() and quicheError()
     *    static methods.
     */
    public static final class ErrorCode {
        /**
         * There is no error or no work to do.
         */
        public static final short DONE = -1;

        /**
         * The provided buffer is too short.
         */
        public static final short BUFFER_TO_SHORT = -2;

        /**
         * Internal error in the HTTP/3 stack.
         */
        public static final short INTERNAL_ERROR = -3;

        /**
         * Endpoint detected that the peer is exhibiting behavior
         * that causes excessive load.
         */
        public static final short EXCESSIVE_LOAD = -4;

        /**
         * Stream ID or Push ID greater that current maximum was
         * used incorrectly, such as exceeding a limit, reducing a limit,
         * or being reused.
         */
        public static final short ID_ERROR = -5;

        /**
         * The endpoint detected that its peer created a stream that it will not
         * accept.
         */
        public static final short STREAM_CREATION_ERROR = -6;

        /**
         * A required critical stream was closed.
         */
        public static final short CLOSED_CRITICAL_STREAM = -7;

        /**
         * No SETTINGS frame at beginning of control stream.
         */
        public static final short MISSING_SETTINGS = -8;

        /**
         * A frame was received which is not permitted in the current state.
         */
        public static final short FRAME_UNEXPECTED = -9;

        /**
         * Frame violated layout or size rules.
         */
        public static final short FRAME_ERROR = -10;

        /**
         * QPACK Header block decompression failure.
         */
        public static final short QPACK_DECOMPRESSION_FAILED = -11;

        /**
         * Error originated from the transport layer.
         */
        public static final short TRANSPORT_ERROR = -12;

        /**
         * The underlying QUIC stream (or connection) doesn't have enough capacity
         * for the operation to complete. The application should retry later on.
         */
        public static final short STREAM_BLOCKED = -13;

        public static final short SETTINGS_ERROR = -14;
        public static final short REQUEST_REJECTED = -15;
        public static final short REQUEST_CANCELED = -16;
        public static final short REQUEST_INCOMPLETE = -17;
        public static final short MESSAGE_ERROR = -18;
        public static final short CONNECT_ERROR = -19;
        public static final short VERSION_FALLBACK = -20;


        public static short h3Error(short code) {
            return (byte)(code & 0xff);
        }

        public static short quicheError(short code) {
            return (byte)((code & 0xff00) >> 8);
        }
    }

}
