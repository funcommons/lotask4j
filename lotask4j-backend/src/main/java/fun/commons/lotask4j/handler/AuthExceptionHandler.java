package fun.commons.lotask4j.handler;

import fun.commons.framework4j.accesstoken.exception.AuthException;
import fun.commons.framework4j.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证异常兜底 — AuthException → HTTP 401 + envelope
 *
 * 前端契约 (frontend/src/api/request.ts): HTTP 401 或 envelope code 10200/10201/10205/10208
 * → 清登录态 + authBus 广播 + 跳 /login。
 * 注意: 仅收窄处理 AuthException, 其余异常仍走 framework4j GlobalExceptionHandler 合约。
 */
@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuth(AuthException ex) {
        log.debug("[Auth] 401: {}", ex.getMessage());
        return ApiResponse.fail(401, ex.getMessage());
    }
}
