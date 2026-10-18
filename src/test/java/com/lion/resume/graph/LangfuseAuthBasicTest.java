package com.lion.resume.graph;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生成 Langfuse OTLP 端点所需的 Basic 认证串
 */
class LangfuseAuthBasicTest {

    /** 默认占位符；实际运行时建议用 -Dlangfuse.pk / -Dlangfuse.sk 覆盖，别把真实密钥提交进仓库 */
    private static final String DEFAULT_PUBLIC_KEY = "pk-lf-240bb790-4556-405b-ab0e-d3ea72f2fbf9";
    private static final String DEFAULT_SECRET_KEY = "sk-lf-f7c3079f-18f6-4b2f-8d96-e09b62fadbcd";

    @Test
    void printLangfuseAuthBasic() {
        String publicKey = System.getProperty("langfuse.pk", DEFAULT_PUBLIC_KEY);
        String secretKey = System.getProperty("langfuse.sk", DEFAULT_SECRET_KEY);

        String auth = generateBasicAuth(publicKey, secretKey);

        System.out.println("生成auth = " + auth);
    }

    /**
     * 生成 {@code Basic base64(pk:sk)}。
     *
     * @param publicKey Langfuse 项目 Public Key
     * @param secretKey Langfuse 项目 Secret Key
     * @return 可直接作为 Authorization 头的值
     */
    private String generateBasicAuth(String publicKey, String secretKey) {
        String token = publicKey + ":" + secretKey;
        String base64 = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        return "Basic " + base64;
    }
}
