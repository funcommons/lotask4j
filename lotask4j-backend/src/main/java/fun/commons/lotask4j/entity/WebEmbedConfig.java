package fun.commons.lotask4j.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Web Embed 组件配置实体
 *
 * 遵循《数据库表分类与命名规范》《PostgreSQL 开发规范 1.2》
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@TableName(value = "asts_web_embed_config", autoResultMap = true)
public class WebEmbedConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID（雪花算法，应用层生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 访问密钥（accessKey），唯一标识一个配置
     */
    private String configKey;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 默认用户 ID（开放模式使用，鉴权模式用于回调后映射）
     */
    private String userId;

    /**
     * 是否开放模式
     * 0: 鉴权模式，需要回调验证
     * 1: 开放模式，无需鉴权
     */
    private Integer isOpen;

    /**
     * 回调验证地址（鉴权模式使用）
     * 后端会 GET 此地址 ?action=verify&accessKey=xxx
     * 业务方需返回 HTTP 200 + JSON {"code":0}
     */
    private String callbackUrl;

    /**
     * 组件配置（JSONB）
     * 结构: { "task-list": {...}, "task-detail": {...}, "task-card": {...} }
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config = new HashMap<>();

    /**
     * 限定组件类型（必填，不能为 all）
     * task-list / task-detail / task-card
     */
    private String componentType;

    /**
     * 允许的域名，逗号分隔
     */
    private String allowedDomains;

    /**
     * 是否启用
     * 0: 禁用, 1: 启用
     */
    private Integer isEnabled;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;

    /**
     * 逻辑删除
     * 0: 未删除, 1: 已删除
     */
    private Integer isDeleted;

    // ==================== 辅助方法 ====================

    /**
     * 判断是否开放模式
     */
    public boolean isOpenMode() {
        return Integer.valueOf(1).equals(isOpen);
    }

    /**
     * 判断是否启用
     */
    public boolean isEnabled() {
        return Integer.valueOf(1).equals(isEnabled);
    }
}
