package fun.commons.lotask4j.controller;

import fun.commons.framework4j.web.ApiResponse;
import fun.commons.lotask4j.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证端点 — client_credentials 换 access_token
 *
 * 蓝本: benefit4j BenefitAuthController (form-encoded body + query 兜底)
 * 前端: frontend/src/api/auth.ts (loginApi)
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Auth API", description = "认证接口")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/api/v1/auth/token")
    @Operation(summary = "client_credentials 签发 access_token")
    public Object postToken(HttpServletRequest request) {
        Map<String, String> params = parseFormParams(request);

        String grantType = params.get("grant_type");
        String clientId = params.get("client_id");
        String clientSecret = params.get("client_secret");
        String scope = params.get("scope");

        // query string 兜底
        if (grantType == null) grantType = request.getParameter("grant_type");
        if (clientId == null) clientId = request.getParameter("client_id");
        if (clientSecret == null) clientSecret = request.getParameter("client_secret");
        if (scope == null) scope = request.getParameter("scope");

        return authService.postToken(grantType, clientId, clientSecret, scope);
    }

    private Map<String, String> parseFormParams(HttpServletRequest request) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8));
            String body = reader.lines().collect(Collectors.joining());
            if (body.isEmpty()) return Map.of();

            Map<String, String> params = new LinkedHashMap<>();
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    params.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
            return params;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
