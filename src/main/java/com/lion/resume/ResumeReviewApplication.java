package com.lion.resume;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 企业招聘简历审查系统启动类
 *
 * @author lion
 */
@EnableAsync
@SpringBootApplication
public class ResumeReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeReviewApplication.class, args);
        System.out.println("""

                ======================================================
                 简历审查系统启动成功
                 前端地址: http://localhost:5173
                 接口文档: http://localhost:8080/swagger-ui.html
                 SSE 订阅: GET /api/review/stream/{taskId}
                ======================================================
                """);
    }
}
