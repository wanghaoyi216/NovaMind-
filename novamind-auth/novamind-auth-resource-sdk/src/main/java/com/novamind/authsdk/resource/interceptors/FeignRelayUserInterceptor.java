package com.novamind.authsdk.resource.interceptors;

import com.novamind.auth.common.constants.JwtConstants;
import com.novamind.common.utils.TokenContext;
import com.novamind.common.utils.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class FeignRelayUserInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return;
        }
        template.header(JwtConstants.USER_HEADER, userId.toString());
        String token = TokenContext.getToken();
        if (token != null && !token.isBlank()) {
            template.header(JwtConstants.TOKEN_HEADER, token);
        }
    }
}
