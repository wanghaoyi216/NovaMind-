package com.novamind.common.filters;

import com.novamind.common.constants.Constant;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@WebFilter(filterName = "requestIdFilter", urlPatterns = "/**")
public class RequestIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 1.获取请求头中的requestId，若无则自动生成UUID兜底
        String requestId = request.getHeader(Constant.REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        // 2.回写到响应头中，方便前端与调用方链路排查
        response.setHeader(Constant.REQUEST_ID_HEADER, requestId);

        try {
            // 3.存入MDC日志上下文
            MDC.put(Constant.REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            // 4.清理MDC上下文，防止线程复用污染
            MDC.remove(Constant.REQUEST_ID_HEADER);
        }
    }
}

