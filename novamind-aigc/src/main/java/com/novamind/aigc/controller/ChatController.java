package com.novamind.aigc.controller;

import com.novamind.aigc.dto.ChatDTO;
import com.novamind.aigc.service.ChatService;
import com.novamind.aigc.vo.ChatEventVO;
import com.novamind.aigc.vo.TemplateVO;
import com.novamind.common.annotations.NoWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private static final TemplateVO TEMPLATE_VO = new TemplateVO();

    @NoWrapper
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> chat(@RequestBody ChatDTO chatDTO) {
        return this.chatService.chat(chatDTO);
    }

    @PostMapping("/stop")
    public void stop(@RequestParam("sessionId") String sessionId) {
        this.chatService.stop(sessionId);
    }

    @PostMapping("/text")
    public String chatText(@RequestBody String question) {
        return this.chatService.chatText(question);
    }

    @GetMapping("/templates")
    public TemplateVO getTemplates() {
        return TEMPLATE_VO;
    }

}
