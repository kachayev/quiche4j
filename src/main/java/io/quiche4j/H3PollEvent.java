package io.quiche4j;

public interface H3PollEvent {
    public abstract void onHeader(long streamId, String name, String value);
    public abstract void onData(long streamId);
    public abstract void onFinished(long streamId);
}