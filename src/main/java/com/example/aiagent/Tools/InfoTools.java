package com.example.aiagent.Tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfoTools implements AgentToolsInterface {

    @Value("${spring.ai.deepseek.chat.model}")
    private String model;


    @Tool(description = "获取当前模型信息")
    public String getModelInfo() {
        return "我是"+model+"模型,有什么问题可以问我";
    }
}
