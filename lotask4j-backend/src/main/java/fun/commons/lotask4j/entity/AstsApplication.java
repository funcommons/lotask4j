package fun.commons.lotask4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import fun.commons.framework4j.openid.annotation.OpenId;
import lombok.Getter;
import lombok.Setter;

/**
 * 接入应用表 (asts_application) — client_credentials 凭据载体。
 *
 * 蓝本: benefit4j UbmaApplication。本期仅建表预留 (控制台走合成 ADMIN 凭据,
 * lotask4j.security.admin.*), 为未来 client 租户凭据签发铺路。
 *
 * TODO: 启用 framework4j-sensitive 后, appSecret 改
 *       {@code @TableField(typeHandler = LazyEncryptedFieldTypeHandler.class)}
 *       + {@code @Sensitive(value = SensitiveRule.CUSTOM, pattern = "2,4,0")}
 *       实现 AES-256-GCM 落库加密 + 响应脱敏 (对齐 benefit4j)。
 */
@Getter
@Setter
@TableName("asts_application")
public class AstsApplication {

    /** 主键 (雪花 ID, OpenID 混淆暴露) */
    @TableId(type = IdType.ASSIGN_ID)
    @OpenId
    private Long id;

    /** OAuth2 client_secret (明文暂存; 敏感模块启用后升级 AES-GCM, 见类注释 TODO) */
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
