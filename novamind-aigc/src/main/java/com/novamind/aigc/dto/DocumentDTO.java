package com.novamind.aigc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {

    @NotEmpty(message = "文档列表不能为空")
    @Size(max = 100, message = "单次最多上传100个文档")
    @Valid
    List<Document> documents;

    public record Document(
            @NotBlank(message = "文档标题不能为空")
            @Size(max = 200, message = "标题长度不能超过200个字符")
            String title,

            @NotBlank(message = "文档内容不能为空")
            @Size(max = 100000, message = "内容长度不能超过100000个字符")
            String content,

            @Size(max = 100, message = "分类长度不能超过100个字符")
            String category,

            @Size(max = 20, message = "标签数量不能超过20个")
            List<@Size(max = 50, message = "单个标签长度不能超过50个字符") String> tags
    ) {
    }
}
