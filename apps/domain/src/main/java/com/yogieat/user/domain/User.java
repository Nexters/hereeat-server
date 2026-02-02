package com.yogieat.user.domain;

public record User(
        Long id,
        String nickname,
        String sessionKey
) {
}
