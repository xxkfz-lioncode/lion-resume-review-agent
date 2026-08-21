package com.lion.resume.common;

import lombok.Data;

/**
 * 统一响应结构
 */
@Data
public class R<T> {

    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok() {
        return build(200, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return build(200, "success", data);
    }

    public static <T> R<T> fail(int code, String message) {
        return build(code, message, null);
    }

    public static <T> R<T> build(int code, String message, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }
}
