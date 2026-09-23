package com.novamind.aigc.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioTranscriptionOptions;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisModel;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisPrompt;
import com.alibaba.cloud.ai.dashscope.audio.transcription.AudioTranscriptionModel;
import com.novamind.aigc.service.AudioService;
import com.novamind.aigc.service.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.nio.ByteBuffer;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "audio-type", havingValue = "DASHSCOPE")
public class DashScopeAudioServiceImpl implements AudioService {

    private static final String DEFAULT_MODEL = "sensevoice-v1";

    private final SpeechSynthesisModel speechSynthesisModel;
    private final AudioTranscriptionModel audioTranscriptionModel;
    private final FileStorage fileStorage;

    @Override
    public ResponseBodyEmitter ttsStream(String text) {
        var prompt = new SpeechSynthesisPrompt(text);
        var response = this.speechSynthesisModel.stream(prompt);
        var emitter = new ResponseBodyEmitter();

        response.subscribe(
                speechResponse -> {
                    try {
                        var byteBuffer = speechResponse.getResult().getOutput().getAudio();
                        byte[] bytes = new byte[byteBuffer.remaining()];
                        byteBuffer.get(bytes);
                        emitter.send(bytes);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );

        return emitter;
    }

    @Override
    public String stt(MultipartFile multipartFile) {
        try {
            String suffix = StrUtil.subAfter(multipartFile.getOriginalFilename(), ".", true);
            var path = StrUtil.format("{}/{}.{}",
                    DateUtil.format(DateUtil.date(), "yyyy/MM/dd"),
                    IdUtil.fastSimpleUUID(),
                    suffix);
            String url = this.fileStorage.uploadFile(path, multipartFile.getInputStream(), multipartFile.getSize());

            var resource = new UrlResource(url);
            var options = DashScopeAudioTranscriptionOptions.builder()
                    .withModel(DEFAULT_MODEL)
                    .build();
            var audioTranscriptionPrompt = new AudioTranscriptionPrompt(resource, options);
            var response = this.audioTranscriptionModel.call(audioTranscriptionPrompt);

            return response.getResult().getOutput();
        } catch (Exception e) {
            throw new RuntimeException("语音识别失败", e);
        }
    }

}