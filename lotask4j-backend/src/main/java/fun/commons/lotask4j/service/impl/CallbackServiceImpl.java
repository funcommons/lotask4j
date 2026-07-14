package fun.commons.lotask4j.service.impl;

import fun.commons.lotask4j.service.CallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 回调验证服务实现
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
public class CallbackServiceImpl implements CallbackService {

    @Autowired
    @Qualifier("callbackRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public void verify(String callbackUrl, String accessKey) {
        // 构造回调 URL
        String separator = callbackUrl.contains("?") ? "&" : "?";
        String verifyUrl = callbackUrl + separator
                + "action=verify&accessKey=" + accessKey;

        log.info("[Web Embed] 回调验证: accessKey={}, url={}", accessKey, verifyUrl);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(verifyUrl, Map.class);

            // 1. 必须 HTTP 200
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalArgumentException(
                        "回调验证失败：HTTP " + response.getStatusCode().value());
            }

            // 2. body.code 必须为 0
            Map body = response.getBody();
            if (body == null) {
                throw new IllegalArgumentException("回调验证失败：响应为空");
            }

            Object code = body.get("code");
            if (code == null || !"0".equals(String.valueOf(code))) {
                throw new IllegalArgumentException(
                        "回调验证失败：code != 0, actual=" + code);
            }

            log.info("[Web Embed] 回调验证成功: accessKey={}", accessKey);

        } catch (RestClientException e) {
            log.error("[Web Embed] 回调验证异常: accessKey={}, error={}", accessKey, e.getMessage());
            throw new IllegalArgumentException("回调验证失败：" + e.getMessage());
        }
    }
}
