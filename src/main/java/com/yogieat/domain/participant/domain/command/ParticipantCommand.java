package com.yogieat.domain.participant.domain.command;

import java.util.List;

public record ParticipantCommand() {
    public record Create(
            String accessKey,
            Double distance,
            List<String> dislikes,
            List<String> preferences
    ) {
        public static Create of(
                String accessKey,
                Double distance,
                List<String> dislikes,
                List<String> preferences
        ) {
            return new Create(
                    accessKey,
                    distance,
                    dislikes,
                    preferences
            );
        }
    }
}
