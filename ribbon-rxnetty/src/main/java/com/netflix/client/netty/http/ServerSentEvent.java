package com.netflix.client.netty.http;

public class ServerSentEvent<T> {
    private final String id;
    private final String name;
    private final T entity;
    
    public ServerSentEvent(String eventId, String eventName, T eventData) {
        super();
        this.id = eventId;
        this.name = eventName;
        this.entity = eventData;
    }

    public final String getId() {
        return id;
    }

    public final String getName() {
        return name;
    }

    public final T getEntity() {
        return entity;
    }
}
