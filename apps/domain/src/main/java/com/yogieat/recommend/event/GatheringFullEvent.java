package com.yogieat.recommend.event;

import com.yogieat.common.Region;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GatheringFullEvent extends ApplicationEvent {
    private final Long gatheringId;
    private final Region region;
    private final Integer peopleCount;

    public GatheringFullEvent(Object source, Long gatheringId, Region region, Integer peopleCount) {
        super(source);
        this.gatheringId = gatheringId;
        this.region = region;
        this.peopleCount = peopleCount;
    }
}
