package com.novamind.common.autoconfigure.mvc.advice;

import com.novamind.common.constants.Constant;
import com.novamind.common.domain.R;
import com.novamind.common.exceptions.CommonException;
import com.novamind.common.exceptions.DbException;
import com.novamind.common.utils.WebUtils;
import feign.FeignException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class CommonExceptionAdvice {

    @ExceptionHandler(DbException.class)
    public Object handleDbException(DbException e) {
        log.error("MySQL数据库操作异常 -> ", e);
        return processResponse(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Object handleDuplicateKeyException(DuplicateKeyException e) {
        log.error("数据库主键或唯一索引冲突 -> ", e);
        return processResponse(HttpStatus.BAD_REQUEST.value(), 409, "数据已存在，请勿重复操作");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("数据完整性约束异常 -> ", e);
        return processResponse(HttpStatus.BAD_REQUEST.value(), 400, "数据约束校验失败，请检查输入");
    }

    @ExceptionHandler(CommonException.class)
    public Object handleCommonException(CommonException e) {
        log.error("自定义业务异常 -> {} , 状态码：{}, 异常原因：{} ", e.getClass().getName(), e.getStatus(), e.getMessage());
        log.debug("异常堆栈", e);
        return processResponse(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(FeignException.class)
    public Object handleFeignException(FeignException e) {
        log.error("Feign远程调用异常 -> status: {}, content: {}", e.status(), e.contentUTF8(), e);
        return processResponse(e.status() > 0 ? e.status() : 500, e.status() > 0 ? e.status() : 500, "下游微服务调用异常: " + e.contentUTF8());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors()
                .stream().map(ObjectError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.error("请求参数校验失败 -> {}", msg);
        return processResponse(400, 400, msg);
    }

    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e) {
        log.error("请求参数绑定异常 -> BindException, {}", e.getMessage());
        return processResponse(400, 400, "请求参数格式错误");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("请求Body解析失败 -> {}", e.getMessage());
        return processResponse(400, 400, "请求体格式错误或缺失");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParameterException(MissingServletRequestParameterException e) {
        log.error("缺少必需的请求参数 -> {}", e.getParameterName());
        return processResponse(400, 400, "缺少必需的参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("参数类型转换失败 -> 参数名: {}, 传入值: {}", e.getName(), e.getValue());
        return processResponse(400, 400, "参数类型不匹配: " + e.getName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("不支持的HTTP方法 -> {}", e.getMethod());
        return processResponse(405, 405, "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler(ServletException.class)
    public Object handleServletException(ServletException e) {
        log.error("Servlet参数异常 -> {}", e.getMessage());
        return processResponse(400, 400, "请求参数异常");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        log.error("请求参数约束异常 -> {}", msg);
        return processResponse(HttpStatus.OK.value(), HttpStatus.BAD_REQUEST.value(), msg);
    }

    @ExceptionHandler(Exception.class)
    public Object handleRuntimeException(Exception e) {
        HttpServletRequest request = WebUtils.getRequest();
        String traceId = MDC.get(Constant.REQUEST_ID_HEADER);
        if (null == request) {
            log.error("服务器未捕获异常 [TraceId: {}] -> ", traceId, e);
        } else {
            log.error("服务器未捕获异常 [TraceId: {}] URI: {} -> ", traceId, request.getRequestURI(), e);
        }
        return processResponse(500, 500, "服务器内部繁忙，请稍后再试");
    }

    private Object processResponse(int status, int code, String msg) {
        // 1.标记响应异常已处理（避免重复处理）
        WebUtils.setResponseHeader(Constant.BODY_PROCESSED_MARK_HEADER, "true");
        // 2.如果是网关请求，http状态码修改为200返回，前端基于业务状态码code来判断状态
        // 如果是微服务请求，http状态码基于异常原样返回，微服务自己做fallback处理
        return WebUtils.isGatewayRequest() ?
                R.error(code, msg).requestId(MDC.get(Constant.REQUEST_ID_HEADER))
                : ResponseEntity.status(status).body(msg);
    }
}

