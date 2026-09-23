package com.novamind.aigc.service.impl;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.novamind.aigc.service.AudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "audio-type", havingValue = "OPENAI")
public class AudioServiceImpl implements AudioService {

    private final OpenAiAudioSpeechModel openAiAudioSpeechModel;
    private final OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel;

    @Override
    public ResponseBodyEmitter ttsStream(String text) {
        log.info("开始进行文字转语音：{}", text);
        var emitter = new ResponseBodyEmitter();
        var speechPrompt = new SpeechPrompt(text);
        var streamResponse = this.openAiAudioSpeechModel.stream(speechPrompt);

        streamResponse.subscribe(
                speechResponse -> {
                    try {
                        byte[] bytes = speechResponse.getResult().getOutput();
                        emitter.send(bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );

        return emitter;
    }

    @Override
    public String stt(MultipartFile multipartFile) {
        var resource = multipartFile.getResource();
        var prompt = new AudioTranscriptionPrompt(resource);
        var response = this.openAiAudioTranscriptionModel.call(prompt);
        var text = response.getResult().getOutput();
        log.info("音频转文字结果：{}", text);
        return ZhConverterUtil.toSimple(text);
    }

}