package io.quiche4j;

public final class H3EventImpl implements H3Event {
    private long streamId;
    private H3EventType eventType;

    public final H3EventType getEventType() {
        return this.eventType;
    }

    public final void setEventType(int eventType) {
        switch(eventType) {
            case 0:
                this.eventType = H3EventType.HEADERS;
                break;
            case 1:
                this.eventType = H3EventType.DATA;
                break;
            case 2:
                this.eventType = H3EventType.FINISHED;
                break;
            case 3:
                this.eventType = H3EventType.DONE;
                break;
        }
    }

    public final long getStreamId() {
        return this.streamId;
    }

    public final void setStreamId(long streamId) {
        this.streamId = streamId;
    }
}