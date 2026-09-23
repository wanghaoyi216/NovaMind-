package com.novamind.common.utils;

import com.novamind.common.security.TjAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserContext {
    private static final ThreadLocal<Long> TL = new ThreadLocal<>();

    /**
     * 保存用户信息
     * @param userId 用户id
     */
    public static void setUser(Long userId){
        TL.set(userId);
    }

    /**
     * 获取用户
     * @return 用户id
     */
    public static Long getUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = resolveUserId(authentication);
        return userId == null ? TL.get() : userId;
    }

    /**
     * 移除用户信息
     */
    public static void removeUser(){
        TL.remove();
    }

    private static Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication instanceof TjAuthenticationToken token) {
            return token.getUserId();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long value) {
            return value;
        }
        if (principal instanceof Number value) {
            return value.longValue();
        }
        if (principal instanceof String value) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
