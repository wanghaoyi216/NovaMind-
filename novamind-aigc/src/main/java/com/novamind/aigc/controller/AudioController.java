package com.novamind.aigc.controller;

import com.novamind.aigc.service.AudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@RestController
@RequestMapping("/audio")
@RequiredArgsConstructor
public class AudioController {

    private final AudioService audioService;

    @PostMapping(value = "tts-stream", produces = "audio/mp3")
    public ResponseBodyEmitter ttsStream(@RequestBody String text) {
        return this.audioService.ttsStream(text);
    }

    @PostMapping("stt")
    public String stt(@RequestParam("audioFile") MultipartFile multipartFile) {
        return this.audioService.stt(multipartFile);
    }

}