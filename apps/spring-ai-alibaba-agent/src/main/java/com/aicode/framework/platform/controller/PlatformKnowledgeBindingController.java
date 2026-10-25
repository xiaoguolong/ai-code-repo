package com.aicode.framework.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aicode.framework.dto.ApiResponse;
import com.aicode.framework.knowledge.dto.KnowledgeBindingListResponse;
import com.aicode.framework.knowledge.dto.KnowledgeImportResponse;
import com.aicode.framework.platform.application.KnowledgeBindingUseCase;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库绑定 API（Week 25）。
 *
 * <p>导入/删除仅 admin；列表所有登录用户可看（同租户）。绑定的是 enterprise 知识库，
 * 平台不存正文，只存「租户 → 企业知识库 + 归属企业用户」的映射。</p>
 */
@RestController
@RequestMapping("/api/v1/platform/knowledge-bindings")
public class PlatformKnowledgeBindingController {

    private final KnowledgeBindingUseCase bindingUseCase;

    public PlatformKnowledgeBindingController(KnowledgeBindingUseCase bindingUseCase) {
        this.bindingUseCase = bindingUseCase;
    }

    /**
     * 从 enterprise 导入当前管理员名下的知识库（幂等）。
     *
     * @return 导入结果
     */
    @PostMapping("/import")
    public ApiResponse<KnowledgeImportResponse> importBindings() {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(
                KnowledgeImportResponse.from(bindingUseCase.importFromEnterprise(callerUserId)));
    }

    /**
     * 列出当前租户的知识库绑定。
     *
     * @return 绑定列表
     */
    @GetMapping
    public ApiResponse<KnowledgeBindingListResponse> list() {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(KnowledgeBindingListResponse.from(bindingUseCase.list(callerUserId)));
    }

    /**
     * 删除绑定（仅 admin）。
     *
     * @param bindingId 绑定 ID
     * @return 空
     */
    @DeleteMapping("/{bindingId}")
    public ApiResponse<Void> delete(@PathVariable String bindingId) {
        StpUtil.checkLogin();
        long callerUserId = StpUtil.getLoginIdAsLong();
        bindingUseCase.delete(callerUserId, bindingId);
        return ApiResponse.success(null);
    }
}
