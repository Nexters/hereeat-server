package com.yogieat.domain.user.repository;

import com.yogieat.domain.user.service.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserCoreRepository implements UserRepository {
    private final UserJpaRepository userJpaRepository;
}
