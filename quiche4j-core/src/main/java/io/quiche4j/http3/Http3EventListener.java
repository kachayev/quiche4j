package io.quiche4j.http3;

import java.util.List;

/**
 * A set of callbacks for HTTP/3 connection event.
 */
public interface Http3EventListener {
    /**
     * Request/response headers were received.    
     * 
     * <p>The application should validate pseudo-headers and headers.    
     * 
     * <p>{@code hasBody} flag reports whether data will follow the headers on the stream.
     */
    void onHeaders(long streamId, List<Http3Header> headers, boolean hasBody);

    /**
     * Data was received.
     *
     * <p>This indicates that the application can use the {@link Http3Connection#recvBody}
     * method to retrieve the data from the stream.
     *
     * <p>This event will keep being reported until all the available data is
     * retrieved by the application.
     */
    void onData(long streamId);

    /**
     * Stream was closed.
     */
    void onFinished(long streamId);
}