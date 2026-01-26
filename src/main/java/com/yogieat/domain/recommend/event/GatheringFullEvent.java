package com.yogieat.domain.recommend.event;

import com.yogieat.domain.common.Place;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GatheringFullEvent extends ApplicationEvent {
    private final Long gatheringId;
    private final Place place;
    private final Integer headCount;

    public GatheringFullEvent(Object source, Long gatheringId, Place place, Integer headCount) {
        super(source);
        this.gatheringId = gatheringId;
        this.place = place;
        this.headCount = headCount;
    }
}
