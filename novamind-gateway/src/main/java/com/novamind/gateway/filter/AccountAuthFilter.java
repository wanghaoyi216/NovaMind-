package com.novamind.gateway.filter;

import com.novamind.authsdk.gateway.util.AuthUtil;
import com.novamind.common.domain.R;
import com.novamind.common.domain.dto.LoginUserDTO;
import com.novamind.gateway.config.AuthProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

import static com.novamind.auth.common.constants.JwtConstants.*;

@Component
public class AccountAuthFilter implements GlobalFilter, Ordered {

    private static final Set<String> GATEWAY_PREFIXES = Set.of(
            "ms", "as", "ds", "sms", "us", "cs", "os", "ss", "ls", "ps", "ts", "es", "rs", "prs", "ais"
    );

    private final AuthUtil authUtil;
    private final AuthProperties authProperties;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public AccountAuthFilter(AuthUtil authUtil, AuthProperties authProperties) {
        this.authUtil = authUtil;
        this.authProperties = authProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1.获取请求request信息
        ServerHttpRequest request = exchange.getRequest();
        // String method = request.getMethodValue();
        String method = request.getMethod().name();
        String path = request.getPath().toString();
        String antPath = method + ":" + normalizePath(path);

        // 2.判断是否是无需登录的路径
        if(isExcludePath(antPath)){
            // 直接放行
            return chain.filter(exchange);
        }

        // 3.尝试获取用户信息
        List<String> authHeaders = exchange.getRequest().getHeaders().get(AUTHORIZATION_HEADER);
        String token = authHeaders == null ? "" : authHeaders.get(0);
        R<LoginUserDTO> r = authUtil.parseToken(token);

        // 4.如果用户是登录状态，尝试更新请求头，传递用户信息
        if (r.success()) {
            exchange = exchange.mutate()
                    .request(builder -> builder
                            .header(USER_HEADER, r.getData().getUserId().toString())
                            .header(TOKEN_HEADER, token)
                    )
                    .build();
        }

        // 5.校验权限
        authUtil.checkAuth(antPath, r);

        // 6.放行
        return chain.filter(exchange);
    }

    private boolean isExcludePath(String antPath) {
        for (String pathPattern : authProperties.getExcludePath()) {
            if(antPathMatcher.match(pathPattern, antPath)){
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        if (path == null || !path.startsWith("/")) {
            return path;
        }
        int secondSlash = path.indexOf('/', 1);
        if (secondSlash <= 0) {
            return path;
        }
        String prefix = path.substring(1, secondSlash);
        if (!GATEWAY_PREFIXES.contains(prefix)) {
            return path;
        }
        String normalized = path.substring(secondSlash);
        return normalized.isEmpty() ? "/" : normalized;
    }

    @Override
    public int getOrder() {
        return 1000;
    }
}
