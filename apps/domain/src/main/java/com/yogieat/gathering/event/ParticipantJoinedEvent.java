package com.yogieat.gathering.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ParticipantJoinedEvent extends ApplicationEvent {
    private final String accessKey;
    private final Long currentCount;
    private final Integer maxCount;

    public ParticipantJoinedEvent(Object source, String accessKey, Long currentCount, Integer maxCount) {
        super(source);
        this.accessKey = accessKey;
        this.currentCount = currentCount;
        this.maxCount = maxCount;
    }
}
