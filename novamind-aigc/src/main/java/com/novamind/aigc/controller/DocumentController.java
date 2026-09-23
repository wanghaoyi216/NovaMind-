package com.novamind.aigc.controller;

import com.novamind.aigc.dto.DocumentDTO;
import com.novamind.aigc.service.DocumentService;
import com.novamind.aigc.vo.DocumentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/knowledge/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 批量上传文档
     */
    @PostMapping
    public Boolean createDocument(@Valid @RequestBody DocumentDTO documentDTO) {
        return documentService.createDocument(documentDTO);
    }

    /**
     * 查询文档列表（分页）
     */
    @GetMapping
    public List<DocumentVO> listDocuments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String category
    ) {
        return documentService.listDocuments(page, size, category);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{documentId}")
    public Boolean deleteDocument(@PathVariable String documentId) {
        return documentService.deleteDocument(documentId);
    }

    /**
     * 查询文档详情
     */
    @GetMapping("/{documentId}")
    public DocumentVO getDocumentById(@PathVariable String documentId) {
        return documentService.getDocumentById(documentId);
    }

    /**
     * 更新文档状态（内部调用）
     */
    @PutMapping("/{documentId}/status")
    public Boolean updateDocumentStatus(
            @PathVariable String documentId,
            @RequestParam String status,
            @RequestParam(required = false) String errorMessage
    ) {
        return documentService.updateDocumentStatus(documentId, status, errorMessage);
    }
}
