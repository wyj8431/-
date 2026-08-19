package com.example.lowcode.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数无效"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权执行此操作"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    TEMPLATE_TAG_IN_USE(HttpStatus.CONFLICT, "标签仍被模板引用，无法删除"),
    TEMPLATE_IN_USE(HttpStatus.CONFLICT, "模板仍被使用，无法删除"),
    DESIGN_VERSION_CONFLICT(HttpStatus.CONFLICT, "设计稿已被更新"),
    INVALID_SCHEMA(HttpStatus.BAD_REQUEST, "设计稿结构无效"),
    INVALID_UPLOAD(HttpStatus.BAD_REQUEST, "上传文件无效"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
