package com.novamind.gateway.exception.handler;

import com.novamind.common.constants.Constant;
import com.novamind.common.domain.R;
import com.novamind.common.exceptions.CommonException;
import com.novamind.common.exceptions.ForbiddenException;
import com.novamind.common.exceptions.UnauthorizedException;
import com.novamind.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.novamind.common.constants.ErrorInfo.Code.FAILED;

@Slf4j
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler, Ordered {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String message;
        int code = FAILED;
        HttpStatus httpStatus = HttpStatus.OK;

        if (ex instanceof UnauthorizedException e) {
            code = 401;
            message = e.getMessage() != null ? e.getMessage() : "未登录或登录凭证已过期";
            httpStatus = HttpStatus.UNAUTHORIZED;
        } else if (ex instanceof ForbiddenException e) {
            code = 403;
            message = e.getMessage() != null ? e.getMessage() : "没有访问权限";
            httpStatus = HttpStatus.FORBIDDEN;
        } else if (ex instanceof CommonException e) {
            code = e.getCode();
            message = e.getMessage();
        } else if (ex instanceof NotFoundException) {
            code = 404;
            message = "请求的服务或接口不存在: " + exchange.getRequest().getPath();
        } else if (ex instanceof TimeoutException) {
            code = 504;
            message = "网关转发调用超时，请稍后再试";
        } else if (ex instanceof ResponseStatusException rse) {
            code = rse.getStatusCode().value();
            message = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            if (rse.getStatusCode() instanceof HttpStatus status) {
                httpStatus = status;
            }
        } else {
            code = 500;
            message = "网关内部路由异常，下游服务可能未启动或网络异常";
            writeLog(exchange, ex);
        }

        // 设置响应状态码与响应类型
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 提取 TraceId / RequestId
        String requestId = exchange.getRequest().getHeaders().getFirst(Constant.REQUEST_ID_HEADER);
        if (requestId == null) {
            List<String> resRequestIds = response.getHeaders().get(Constant.REQUEST_ID_HEADER);
            if (resRequestIds != null && !resRequestIds.isEmpty()) {
                requestId = resRequestIds.get(0);
            }
        }

        R<Object> r = R.error(code, message).requestId(requestId);
        byte[] resp = JsonUtils.toJsonStr(r).getBytes(StandardCharsets.UTF_8);

        return response.writeWith(
                Mono.fromSupplier(() -> response.bufferFactory().wrap(resp))
        );
    }

    private void writeLog(ServerWebExchange exchange, Throwable ex) {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        log.error("网关异常 -> URI: {}, Method: {}, Query: {}, 错误信息: ",
                request.getPath(), request.getMethod(), uri.getQuery(), ex);
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}