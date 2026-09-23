package com.novamind.aigc.controller;

import com.novamind.aigc.service.ChatSessionService;
import com.novamind.aigc.vo.ChatSessionVO;
import com.novamind.aigc.vo.ExportSessionVO;
import com.novamind.aigc.vo.MessageVO;
import com.novamind.aigc.vo.SessionVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final ChatSessionService chatSessionService;

    @PostMapping
    public SessionVO createSession(@RequestParam(value = "n", defaultValue = "3") Integer num) {
        return this.chatSessionService.createSession(num);
    }

    @GetMapping("/hot")
    public List<SessionVO.Example> hotExamples(@RequestParam(value = "n", defaultValue = "3") Integer num) {
        return this.chatSessionService.hotExamples(num);
    }

    @GetMapping("/{sessionId}")
    public List<MessageVO> queryBySessionId(@PathVariable("sessionId") String sessionId) {
        return this.chatSessionService.queryBySessionId(sessionId);
    }

    @GetMapping("/history")
    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        return this.chatSessionService.queryHistorySession();
    }

    @DeleteMapping("/history")
    public void deleteHistorySession(@RequestParam("sessionId") String sessionId) {
        this.chatSessionService.deleteHistorySession(sessionId);
    }

    @PutMapping("/history")
    public void updateTitle(@RequestParam("sessionId") String sessionId,
                            @RequestParam("title") String title) {
        this.chatSessionService.updateTitle(sessionId, title);
    }

    // 根据用户Id和会话Id查询用户聊天内容
    @GetMapping("/export")
    public ExportSessionVO getSession(@RequestParam("sessionId")  String sessionId) {
        return this.chatSessionService.getSession(sessionId);
    }
}