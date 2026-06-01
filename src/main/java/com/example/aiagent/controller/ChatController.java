package com.example.aiagent.controller;


import com.example.aiagent.Tools.ResultTools;
import com.example.aiagent.Tools.ScanTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RequestMapping("/chat")
@RestController

public class ChatController {

    private final ChatClient chatClient;

    @Autowired
    private ResultTools resultTools;

    @Autowired
    private ScanTools scanTools;
    /**
     * 注入ChatClient
     * @param chatClient
     */
    public ChatController(ChatClient.Builder chatClient) {
        this.chatClient = chatClient.build();
    }


    /**
     * 聊天调用(返回完整文本)
     */
    @PostMapping("/call")
    public String chatCall(@RequestBody String question) {
        return chatClient.prompt(question)
                .tools(resultTools, scanTools)
                .call()
                .content();
    }


    /**
     * 聊天流式返回(SSE流式)
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody String question) {
        return chatClient.prompt(question)
                .tools(resultTools, scanTools)
                .stream()
                .content();
    }
}
