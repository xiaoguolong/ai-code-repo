package com.aicode.enterprise.controller;

import com.aicode.enterprise.application.InternalKnowledgeSearchCommand;
import com.aicode.enterprise.application.InternalKnowledgeUseCase;
import com.aicode.enterprise.dto.ApiResponse;
import com.aicode.enterprise.dto.InternalKnowledgeResponse;
import com.aicode.enterprise.dto.InternalSearchRequest;
import com.aicode.enterprise.infrastructure.internal.InternalTokenGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部知识库端点（Week 25，仅平台调用）。
 *
 * <p><b>不走 Sa-Token</b>：由 {@link InternalTokenGuard} 校验 {@code X-Internal-Token}，
 * 未配置令牌时 fail closed（403）。只读：检索与清单，不建会话、不写 Token 记录。</p>
 */
@RestController
@RequestMapping("/api/v1/internal/knowledge")
public class InternalKnowledgeController {

    private final InternalKnowledgeUseCase useCase;
    private final InternalTokenGuard tokenGuard;

    public InternalKnowledgeController(
            InternalKnowledgeUseCase useCase,
            InternalTokenGuard tokenGuard
    ) {
        this.useCase = useCase;
        this.tokenGuard = tokenGuard;
    }

    /**
     * 列出平台用户映射到的本地用户的知识库（供平台“导入知识库”）。
     *
     * @param token          内部令牌
     * @param platformUserId 平台用户 ID
     * @return 本地用户 ID + 知识库清单
     */
    @GetMapping("/bases")
    public ApiResponse<InternalKnowledgeResponse> bases(
            @RequestHeader(value = InternalTokenGuard.HEADER_NAME, required = false) String token,
            @RequestParam long platformUserId
    ) {
        tokenGuard.verify(token);
        return ApiResponse.success(InternalKnowledgeResponse.catalog(useCase.listBases(platformUserId)));
    }

    /**
     * 检索知识库切片（不生成答案）。
     *
     * @param token   内部令牌
     * @param request 检索请求
     * @return 知识库信息 + 命中切片
     */
    @PostMapping("/search")
    public ApiResponse<InternalKnowledgeResponse> search(
            @RequestHeader(value = InternalTokenGuard.HEADER_NAME, required = false) String token,
            @Valid @RequestBody InternalSearchRequest request
    ) {
        tokenGuard.verify(token);
        return ApiResponse.success(InternalKnowledgeResponse.search(useCase.search(
                new InternalKnowledgeSearchCommand(
                        request.enterpriseUserId(), request.knowledgeBaseId(),
                        request.query(), request.topK()))));
    }
}
