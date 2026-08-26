package com.lion.resume.graph.strategy;

import com.alibaba.cloud.ai.graph.KeyStrategy;

/**
 * 自定义策略
 */
public class CustomStrategy implements KeyStrategy {

    @Override
    public Object apply(Object oldValue, Object newValue) {
        // 逻辑
        return newValue;
    }
}
