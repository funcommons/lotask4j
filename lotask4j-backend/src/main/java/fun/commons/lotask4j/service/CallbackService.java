package fun.commons.lotask4j.service;

/**
 * 回调验证服务
 *
 * 鉴权模式下，向业务系统的 callback_url 发起验证请求
 * 必须返回 HTTP 200 + JSON {"code":0} 才算验证成功
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public interface CallbackService {

    /**
     * 回调验证
     *
     * @param callbackUrl 业务系统回调地址
     * @param accessKey 访问密钥
     * @throws RuntimeException 验证失败时抛出
     */
    void verify(String callbackUrl, String accessKey);
}
