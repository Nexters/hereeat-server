package com.yogieat.gathering.domain.result;

import com.yogieat.gathering.domain.Gathering;

public record GatheringResult(){
    public record ParticipantCount(
            Long currentCount,
            Integer maxCount
    ) {
        public static ParticipantCount of(
                Long currentCount,
                Integer maxCount
        ) {
            return new ParticipantCount(
                    currentCount,
                    maxCount
            );
        }
    }

    public record Create(
            Long gatheringId,
            String accessKey
    ) {
        public static Create of(
                Gathering gathering
        ) {
            return new Create(
                    gathering.id(),
                    gathering.accessKey()
            );
        }
    }
}
