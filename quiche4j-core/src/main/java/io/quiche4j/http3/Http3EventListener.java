package io.quiche4j.http3;

import java.util.List;

public interface Http3EventListener {
    void onHeaders(long streamId, List<Http3Header> headers, boolean hasBody);
    void onData(long streamId);
    void onFinished(long streamId);
}