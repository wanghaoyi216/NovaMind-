package com.novamind.common.utils;

import com.novamind.common.security.TjAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class TokenContext {
    private static final ThreadLocal<String> TL = new ThreadLocal<>();

    /**
     * 保存token信息
     */
    public static void setToken(String token){
        TL.set(token);
    }

    /**
     * 获取token
     */
    public static String getToken(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String token = resolveToken(authentication);
        return token == null ? TL.get() : token;
    }

    /**
     * 移除用户信息
     */
    public static void removeToken(){
        TL.remove();
    }

    private static String resolveToken(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication instanceof TjAuthenticationToken token) {
            return token.getToken();
        }
        Object credentials = authentication.getCredentials();
        return credentials instanceof String value ? value : null;
    }
}
