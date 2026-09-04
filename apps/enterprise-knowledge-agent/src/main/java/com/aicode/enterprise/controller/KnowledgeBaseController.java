package com.aicode.enterprise.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.enterprise.application.CreateKnowledgeBaseUseCase;
import com.aicode.enterprise.application.DeleteKnowledgeBaseUseCase;
import com.aicode.enterprise.application.GetKnowledgeBaseUseCase;
import com.aicode.enterprise.application.ListKnowledgeBasesUseCase;
import com.aicode.enterprise.application.UpdateKnowledgeBaseCommand;
import com.aicode.enterprise.application.UpdateKnowledgeBaseUseCase;
import com.aicode.enterprise.domain.model.KnowledgeBase;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.dto.CreateKnowledgeBaseRequest;
import com.aicode.enterprise.dto.KnowledgeBaseResponse;
import com.aicode.enterprise.dto.UpdateKnowledgeBaseRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库接入。只做校验、取当前用户与协议转换，归属校验在用例层。
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private final CreateKnowledgeBaseUseCase createKnowledgeBaseUseCase;
    private final ListKnowledgeBasesUseCase listKnowledgeBasesUseCase;
    private final GetKnowledgeBaseUseCase getKnowledgeBaseUseCase;
    private final UpdateKnowledgeBaseUseCase updateKnowledgeBaseUseCase;
    private final DeleteKnowledgeBaseUseCase deleteKnowledgeBaseUseCase;

    public KnowledgeBaseController(
            CreateKnowledgeBaseUseCase createKnowledgeBaseUseCase,
            ListKnowledgeBasesUseCase listKnowledgeBasesUseCase,
            GetKnowledgeBaseUseCase getKnowledgeBaseUseCase,
            UpdateKnowledgeBaseUseCase updateKnowledgeBaseUseCase,
            DeleteKnowledgeBaseUseCase deleteKnowledgeBaseUseCase
    ) {
        this.createKnowledgeBaseUseCase = createKnowledgeBaseUseCase;
        this.listKnowledgeBasesUseCase = listKnowledgeBasesUseCase;
        this.getKnowledgeBaseUseCase = getKnowledgeBaseUseCase;
        this.updateKnowledgeBaseUseCase = updateKnowledgeBaseUseCase;
        this.deleteKnowledgeBaseUseCase = deleteKnowledgeBaseUseCase;
    }

    /**
     * 创建知识库。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KnowledgeBaseResponse> create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        KnowledgeBase knowledgeBase = createKnowledgeBaseUseCase.create(userId, request.name(), request.description());
        return ApiResponse.success(KnowledgeBaseResponse.from(knowledgeBase));
    }

    /**
     * 列出当前用户知识库。
     */
    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> list() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<KnowledgeBaseResponse> data = listKnowledgeBasesUseCase.listByUser(userId).stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    /**
     * 知识库详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> get(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(KnowledgeBaseResponse.from(getKnowledgeBaseUseCase.get(userId, id)));
    }

    /**
     * 更新知识库。
     */
    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        KnowledgeBase updated = updateKnowledgeBaseUseCase.update(
                new UpdateKnowledgeBaseCommand(userId, id, request.name(), request.description())
        );
        return ApiResponse.success(KnowledgeBaseResponse.from(updated));
    }

    /**
     * 删除知识库（级联文档与切片）。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        deleteKnowledgeBaseUseCase.delete(userId, id);
        return ApiResponse.success(null);
    }
}
