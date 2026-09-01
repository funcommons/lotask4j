package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.dto.ApplicationCreateRequest;
import fun.commons.lotask4j.dto.ApplicationStatusRequest;
import fun.commons.lotask4j.dto.ApplicationResponse;
import fun.commons.lotask4j.dto.ApplicationSecretResponse;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.web.ApiResponse;
import fun.commons.lotask4j.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Admin 接入应用管理 Controller — client_credentials 凭据签发
 *
 * 蓝本: benefit4j 应用管理。secret 明文仅创建 / reset-secret 响应出现一次,
 * 列表与详情不含 secret。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/applications")
@RequiredArgsConstructor
@RequiresToken(value = "ADMIN")
@Tag(name = "Admin Applications", description = "接入应用管理 (client_credentials 凭据)")
public class AdminApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    @Operation(summary = "分页查询应用列表", description = "keyword 模糊匹配名称; 不含 secret")
    public ApiResponse<Map<String, Object>> listApplications(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize) {

        long total = applicationService.countApplications(keyword, status);
        List<ApplicationResponse> items = applicationService.listApplications(keyword, status, page, pageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("items", items);
        return ApiResponse.success(data);
    }

    @GetMapping("/{id}")
    @Operation(summary = "应用详情 (不含 secret)")
    public ApiResponse<ApplicationResponse> getApplication(@PathVariable("id") Long id) {
        return ApiResponse.success(applicationService.getApplication(id));
    }

    @PostMapping
    @Operation(summary = "创建应用", description = "返回一次性明文 secret, 请立即保存")
    public ApiResponse<ApplicationSecretResponse> createApplication(
            @Valid @RequestBody ApplicationCreateRequest request) {
        return ApiResponse.success(applicationService.createApplication(request));
    }

    @PostMapping("/{id}/reset-secret")
    @Operation(summary = "重置 secret", description = "旧 secret 立即失效, 返回新的一次性明文")
    public ApiResponse<ApplicationSecretResponse> resetSecret(@PathVariable("id") Long id) {
        return ApiResponse.success(applicationService.resetSecret(id));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "启用/停用应用", description = "body: {\"status\": \"ACTIVE\" | \"INACTIVE\"}")
    public ApiResponse<Void> setStatus(@PathVariable("id") Long id,
                                       @Valid @RequestBody ApplicationStatusRequest request) {
        applicationService.setStatus(id, request.getStatus());
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除应用（逻辑删除）")
    public ApiResponse<Void> deleteApplication(@PathVariable("id") Long id) {
        applicationService.deleteApplication(id);
        return ApiResponse.success();
    }
}
