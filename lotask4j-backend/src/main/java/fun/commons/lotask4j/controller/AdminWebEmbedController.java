package fun.commons.lotask4j.controller;

import fun.commons.lotask4j.dto.WebEmbedConfigRequest;
import fun.commons.lotask4j.dto.WebEmbedConfigResponse;
import fun.commons.lotask4j.service.AdminWebEmbedService;
import fun.commons.framework4j.accesstoken.annotation.RequiresToken;
import fun.commons.framework4j.web.ApiResponse;
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
 * Admin Web Embed Controller
 *
 * 提供 Web Embed 配置的 CURD 接口
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/embed-config")
@RequiredArgsConstructor
@RequiresToken(value = "ADMIN")
@Tag(name = "Admin Web Embed", description = "Web Embed 配置管理")
public class AdminWebEmbedController {

    private final AdminWebEmbedService adminWebEmbedService;

    @GetMapping("/configs")
    @Operation(summary = "分页查询配置列表")
    public ApiResponse<Map<String, Object>> listConfigs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer isEnabled,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long pageSize) {

        long total = adminWebEmbedService.countConfigs(keyword, isEnabled);
        List<WebEmbedConfigResponse> items = adminWebEmbedService.listConfigs(keyword, isEnabled, page, pageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("items", items);
        return ApiResponse.success(data);
    }

    @GetMapping("/configs/{id}")
    @Operation(summary = "获取单个配置")
    public ApiResponse<WebEmbedConfigResponse> getConfig(@PathVariable("id") Long id) {
        return ApiResponse.success(adminWebEmbedService.getConfig(id));
    }

    @PostMapping("/configs")
    @Operation(summary = "创建配置")
    public ApiResponse<Long> createConfig(@Valid @RequestBody WebEmbedConfigRequest request) {
        Long id = adminWebEmbedService.createConfig(request);
        return ApiResponse.success(id);
    }

    @PutMapping("/configs/{id}")
    @Operation(summary = "更新配置")
    public ApiResponse<Void> updateConfig(@PathVariable("id") Long id, @Valid @RequestBody WebEmbedConfigRequest request) {
        request.setId(id);
        adminWebEmbedService.updateConfig(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/configs/{id}")
    @Operation(summary = "删除配置（逻辑删除）")
    public ApiResponse<Void> deleteConfig(@PathVariable("id") Long id) {
        adminWebEmbedService.deleteConfig(id);
        return ApiResponse.success();
    }

    @PostMapping("/configs/{id}/toggle")
    @Operation(summary = "启用/禁用配置")
    public ApiResponse<Void> toggleEnabled(@PathVariable("id") Long id, @RequestParam Integer isEnabled) {
        adminWebEmbedService.toggleEnabled(id, isEnabled);
        return ApiResponse.success();
    }

    @GetMapping("/configs/{id}/preview-url")
    @Operation(summary = "生成嵌入预览 URL（相对路径，iframe 同源）")
    public ApiResponse<Map<String, String>> previewUrl(
            @PathVariable("id") Long id,
            @RequestParam(required = false) String componentType,
            @RequestParam(required = false) String taskId) {

        WebEmbedConfigResponse config = adminWebEmbedService.getConfig(id);
        // 预览用配置的 componentType
        if (componentType == null || componentType.isEmpty()) {
            componentType = config.getComponentType();
        }
        // 预览 iframe 用绝对 URL（业务方嵌入时复制）
        String url = adminWebEmbedService.generateAbsoluteEmbedUrl(config.getConfigKey(), componentType, taskId);

        Map<String, String> data = new HashMap<>();
        data.put("url", url);
        return ApiResponse.success(data);
    }
}
