package com.yogieat.datasource.db.core.user;

import com.yogieat.user.service.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserCoreRepository implements UserRepository {
    private final UserJpaRepository userJpaRepository;
}
