package com.aicode.enterprise.controller;

import com.aicode.core.domain.exception.InvalidChatRequestException;
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
     * 列出知识库（两种模式，二选一）。
     *
     * <ul>
     *   <li>{@code platformUserId}：按平台用户的实时 SSO 映射查（Week 25「导入」场景）；</li>
     *   <li>{@code enterpriseUserId}：按企业本地用户查（Week 27「对账」场景，
     *       不依赖 SSO 映射，导入人解绑也不会让对账失效）。</li>
     * </ul>
     *
     * @param token            内部令牌
     * @param platformUserId   平台用户 ID
     * @param enterpriseUserId 企业本地用户 ID
     * @return 本地用户 ID + 知识库清单
     * @throws InvalidChatRequestException 两个参数都给或都不给
     */
    @GetMapping("/bases")
    public ApiResponse<InternalKnowledgeResponse> bases(
            @RequestHeader(value = InternalTokenGuard.HEADER_NAME, required = false) String token,
            @RequestParam(required = false) Long platformUserId,
            @RequestParam(required = false) Long enterpriseUserId
    ) {
        tokenGuard.verify(token);
        if ((platformUserId == null) == (enterpriseUserId == null)) {
            throw new InvalidChatRequestException(
                    "exactly one of platformUserId / enterpriseUserId is required");
        }
        if (platformUserId != null) {
            return ApiResponse.success(
                    InternalKnowledgeResponse.catalog(useCase.listBases(platformUserId)));
        }
        return ApiResponse.success(
                InternalKnowledgeResponse.catalog(useCase.listBasesByEnterpriseUser(enterpriseUserId)));
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
