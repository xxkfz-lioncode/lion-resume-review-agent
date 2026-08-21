package com.lion.resume.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.lion.resume.common.R;
import com.lion.resume.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录鉴权（Sa-Token）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${app.admin.username:admin}")
    private String username;

    @Value("${app.admin.password:admin123}")
    private String password;

    /**
     * 管理员登录
     * <p>校验用户名密码，校验通过后使用 Sa-Token 完成登录并返回 Token。
     *
     * @param body 请求体，包含 username（用户名）与 password（密码）
     * @return 登录成功返回 token 与 username；用户名或密码错误抛出 BizException
     */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String name = body.get("username");
        String pwd = body.get("password");
        if (username.equals(name) && password.equals(pwd)) {
            StpUtil.login("admin");
            return R.ok(Map.of(
                    "token", StpUtil.getTokenValue(),
                    "username", username));
        }
        throw new BizException("用户名或密码错误");
    }

    /**
     * 获取当前登录状态
     *
     * @return 是否已登录（login）及当前用户名（username）
     */
    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        return R.ok(Map.of("login", StpUtil.isLogin(), "username", username));
    }

    /**
     * 退出登录，使当前会话 Token 失效
     *
     * @return 统一响应结果，无业务数据
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        StpUtil.logout();
        return R.ok();
    }
}
