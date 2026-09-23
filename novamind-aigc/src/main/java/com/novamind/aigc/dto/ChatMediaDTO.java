package com.novamind.aigc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多模态输入片段，支持文本、图片、音频、视频或文件链接。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMediaDTO {

    /**
     * 媒体类型：text/image/audio/video/file
     */
    private String type;

    /**
     * 资源 URL，支持 http(s) 地址或可解析 URI
     */
    private String url;

    /**
     * MIME 类型，例如 image/png、audio/mpeg、video/mp4
     */
    private String mimeType;

    /**
     * 文件或资源名称
     */
    private String name;

    /**
     * 资源说明或 OCR / ASR 文本
     */
    private String text;
}
