package com.novamind.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * 网关已完成 JWT 校验后，下游服务使用的轻量认证对象。
 */
public class TjAuthenticationToken extends AbstractAuthenticationToken {

    private final Long userId;
    private final String token;

    public TjAuthenticationToken(Long userId, String token) {
        this(userId, token, List.of());
    }

    public TjAuthenticationToken(Long userId, String token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.token = token;
        setAuthenticated(userId != null);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    @Override
    public String getName() {
        return userId == null ? "" : userId.toString();
    }

    public Long getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }
}
