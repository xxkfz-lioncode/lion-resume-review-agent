package com.lion.resume.common.exception;

import com.lion.resume.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 静态资源/接口路径不存在：应返回 404 而不是 500「系统异常」
     * （此前访问不存在的路径、favicon.ico 都会走到通用 Exception 分支被打成 ERROR 日志）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        log.info("资源不存在: {}", e.getResourcePath());
        return R.fail(404, "接口或资源不存在: /" + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(500, "系统异常: " + e.getMessage());
    }
}
