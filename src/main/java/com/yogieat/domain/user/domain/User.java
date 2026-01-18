package com.yogieat.domain.user.domain;

public record User(
        Long id,
        String nickname,
        String sessionKey
) {
}
