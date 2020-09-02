package io.quiche4j;

public interface H3Event {
    public H3EventType getEventType();

    public void setEventType(int eventType);

    public long getStreamId();

    public void setStreamId(long streamId);
}