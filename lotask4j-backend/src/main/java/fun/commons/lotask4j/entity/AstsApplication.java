package fun.commons.lotask4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import fun.commons.framework4j.openid.annotation.OpenId;
import fun.commons.framework4j.sensitive.annotation.Sensitive;
import fun.commons.framework4j.sensitive.annotation.SensitiveRule;
import fun.commons.framework4j.sensitive.typehandler.LazyEncryptedFieldTypeHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * 接入应用表 (asts_application) — client_credentials 凭据载体。
 *
 * 蓝本: benefit4j UbmaApplication。应用凭据由管理端签发 (AdminApplicationController),
 * client/worker 域换 token (scope 参数选 policy); 控制台走合成 ADMIN 凭据
 * (lotask4j.security.admin.*)。
 *
 * appSecret: AES-256-GCM 落库加密 (framework4j-sensitive LazyEncryptedFieldTypeHandler,
 * select 时透明解密) + JSON 响应脱敏 (2,4,0 = 首露 2 尾露 4)。
 * autoResultMap = true 必须 — typeHandler 的 select 解密依赖它。
 */
@Getter
@Setter
@TableName(value = "asts_application", autoResultMap = true)
public class AstsApplication {

    /** 主键 (雪花 ID, OpenID 混淆暴露; 亦作签名 access-key) */
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    /** OAuth2 client_secret (AES-GCM 密文落库, 读取透明解密; 序列化脱敏 2,4,0) */
    @Sensitive(value = SensitiveRule.CUSTOM, pattern = "2,4,0")
    @TableField(typeHandler = LazyEncryptedFieldTypeHandler.class)
    private String appSecret;

    /** 应用名称 */
    private String name;

    /** 应用描述 */
    private String description;

    /** 状态 (ACTIVE / INACTIVE) */
    private String status;

    /** 创建时间 */
    private java.time.OffsetDateTime createdAt;

    /** 更新时间 */
    private java.time.OffsetDateTime updatedAt;

    /** 逻辑删除 (0 未删 / 1 已删) */
    @TableLogic
    private Short isDeleted;
}
