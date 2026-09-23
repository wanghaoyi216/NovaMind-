package com.novamind.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 意图识别配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.intent")
public class IntentProperties {

    /**
     * 是否启用意图识别
     * 启用后会根据用户问题自动判断是否使用 RAG
     */
    private boolean enabled = true;

    /**
     * 问题最小长度（低于此长度直接跳过 RAG）
     */
    private int minLength = 10;

    /**
     * 长问题阈值（超过此长度默认启用 RAG）
     */
    private int longQuestionThreshold = 30;

    /**
     * RAG 相关关键词列表
     * 包含这些关键词的问题会启用 RAG
     */
    private List<String> ragKeywords = new ArrayList<>(List.of(
            "怎么", "如何", "为什么", "原理", "教程", "学习", "入门", "进阶",
            "方法", "步骤", "流程", "指南", "手册", "文档",
            "配置", "部署", "安装", "搭建", "环境", "设置", "优化",
            "错误", "异常", "问题", "解决", "修复", "调试",
            "什么是", "解释", "介绍", "概念", "定义", "区别", "对比",
            "原理", "机制", "架构", "设计", "模式"
    ));

    /**
     * 闲聊关键词列表
     * 包含这些关键词的问题会跳过 RAG
     */
    private List<String> chatKeywords = new ArrayList<>(List.of(
            "你好", "嗨", "哈喽", "早上好", "下午好", "晚上好",
            "谢谢", "感谢", "谢了", "多谢",
            "再见", "拜拜", "下次见",
            "在吗", "在不在", "有人吗"
    ));
}
