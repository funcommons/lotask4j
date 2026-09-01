package fun.commons.lotask4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fun.commons.framework4j.web.ApiException;
import fun.commons.lotask4j.dto.ApplicationCreateRequest;
import fun.commons.lotask4j.dto.ApplicationResponse;
import fun.commons.lotask4j.dto.ApplicationSecretResponse;
import fun.commons.lotask4j.entity.AstsApplication;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstsApplicationMapper;
import fun.commons.lotask4j.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 接入应用管理服务实现
 *
 * secret 由 SecureRandom 生成 40 字符 base62; 落库经 LazyEncryptedFieldTypeHandler
 * AES-256-GCM 加密 (entity autoResultMap, insert 自动加密 / select 透明解密)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final AstsApplicationMapper applicationMapper;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SECRET_LENGTH = 40;

    @Override
    public ApplicationSecretResponse createApplication(ApplicationCreateRequest request) {
        AstsApplication app = new AstsApplication();
        app.setName(request.getName());
        app.setDescription(request.getDescription());
        app.setStatus("ACTIVE");
        app.setCreatedAt(OffsetDateTime.now());
        app.setUpdatedAt(OffsetDateTime.now());

        String secret = generateSecret();
        app.setAppSecret(secret);
        applicationMapper.insert(app);

        log.info("创建应用: id={}, name={}", app.getId(), app.getName());
        return secretResponse(app, secret);
    }

    @Override
    public ApplicationSecretResponse resetSecret(Long id) {
        AstsApplication app = requireApp(id);
        String secret = generateSecret();
        app.setAppSecret(secret);
        app.setUpdatedAt(OffsetDateTime.now());
        applicationMapper.updateById(app);

        log.info("重置应用 secret: id={}", id);
        return secretResponse(app, secret);
    }

    @Override
    public void setStatus(Long id, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new ApiException(BusinessCode.APPLICATION_STATUS_INVALID.getCode(),
                    BusinessCode.APPLICATION_STATUS_INVALID.getMessage());
        }
        AstsApplication app = requireApp(id);
        app.setStatus(status);
        app.setUpdatedAt(OffsetDateTime.now());
        applicationMapper.updateById(app);
        log.info("应用状态变更: id={}, status={}", id, status);
    }

    @Override
    public void deleteApplication(Long id) {
        requireApp(id);
        applicationMapper.deleteById(id);
        log.info("删除应用 (逻辑删除): id={}", id);
    }

    @Override
    public ApplicationResponse getApplication(Long id) {
        return toResponse(requireApp(id));
    }

    @Override
    public List<ApplicationResponse> listApplications(String keyword, String status, long page, long pageSize) {
        Page<AstsApplication> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<AstsApplication> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like(AstsApplication::getName, keyword);
        }
        if (status != null && !status.isBlank()) {
            q.eq(AstsApplication::getStatus, status);
        }
        q.orderByDesc(AstsApplication::getCreatedAt);
        return applicationMapper.selectPage(p, q).getRecords()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public long countApplications(String keyword, String status) {
        LambdaQueryWrapper<AstsApplication> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like(AstsApplication::getName, keyword);
        }
        if (status != null && !status.isBlank()) {
            q.eq(AstsApplication::getStatus, status);
        }
        return applicationMapper.selectCount(q);
    }

    private AstsApplication requireApp(Long id) {
        AstsApplication app = applicationMapper.selectById(id);
        if (app == null) {
            throw new ApiException(BusinessCode.APPLICATION_NOT_FOUND.getCode(),
                    "应用不存在: " + id);
        }
        return app;
    }

    private String generateSecret() {
        StringBuilder sb = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    private ApplicationSecretResponse secretResponse(AstsApplication app, String secret) {
        ApplicationSecretResponse resp = new ApplicationSecretResponse();
        resp.setId(app.getId());
        resp.setName(app.getName());
        resp.setAppSecret(secret);
        return resp;
    }

    private ApplicationResponse toResponse(AstsApplication app) {
        ApplicationResponse resp = new ApplicationResponse();
        resp.setId(app.getId());
        resp.setName(app.getName());
        resp.setDescription(app.getDescription());
        resp.setStatus(app.getStatus());
        resp.setCreatedAt(app.getCreatedAt());
        resp.setUpdatedAt(app.getUpdatedAt());
        return resp;
    }
}
