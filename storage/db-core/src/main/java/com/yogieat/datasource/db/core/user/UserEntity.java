package com.yogieat.datasource.db.core.user;

import com.yogieat.datasource.db.core.common.BaseEntity;
import com.yogieat.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "t_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseEntity {

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "session_key")
    private String sessionKey;

    @Builder(access = AccessLevel.PRIVATE)
    public UserEntity(
            String nickname,
            String sessionKey) {
        this.nickname = nickname;
        this.sessionKey = sessionKey;
    }

    public static User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getNickname(),
                entity.getSessionKey()
        );
    }
}
