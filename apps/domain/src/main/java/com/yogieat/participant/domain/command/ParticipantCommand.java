package com.yogieat.participant.domain.command;

import java.util.List;

public record ParticipantCommand() {
    public record Create(
            String accessKey,
            String nickname,
            Double distance,
            List<String> dislikes,
            List<String> preferences
    ) {
        public static Create of(
                String accessKey,
                String nickname,
                Double distance,
                List<String> dislikes,
                List<String> preferences
        ) {
            return new Create(
                    accessKey,
                    nickname,
                    distance,
                    dislikes,
                    preferences
            );
        }
    }
}
