package com.lion.resume.controller;

import com.lion.resume.service.LlmService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private LlmService llmService;

    @GetMapping
    public String chat() {
        String str = llmService.call("你好", "我是张三，今天天气如何？");
        return str;
    }
}
