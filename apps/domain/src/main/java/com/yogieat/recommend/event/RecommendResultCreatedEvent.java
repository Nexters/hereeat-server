package com.yogieat.recommend.event;

import com.yogieat.common.Region;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RecommendResultCreatedEvent extends ApplicationEvent {
    private final Long gatheringId;
    private final Region region;
    private final Integer peopleCount;
    private final String accessKey;
    private final long currentCount;

    private RecommendResultCreatedEvent(Object source, Long gatheringId, Region region, Integer peopleCount, String accessKey, long currentCount) {
        super(source);
        this.gatheringId = gatheringId;
        this.region = region;
        this.peopleCount = peopleCount;
        this.accessKey = accessKey;
        this.currentCount = currentCount;
    }

    public static RecommendResultCreatedEvent of(Object source, Long gatheringId, Region region, Integer peopleCount, String accessKey, long currentCount) {
        return new RecommendResultCreatedEvent(source, gatheringId, region, peopleCount, accessKey, currentCount);
    }
}
