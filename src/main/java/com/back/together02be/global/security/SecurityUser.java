package com.back.together02be.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

@Getter
public class SecurityUser extends User {

    private final Long id;
    private final String nickname;

    public SecurityUser(
            Long id,
            String username,
            String password,
            String nickname,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(username, password, authorities);
        this.id = id;
        this.nickname = nickname;
    }
}
