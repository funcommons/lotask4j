package fun.commons.lotask4j.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * asts_task 月分区运维 Mapper (DDL 动态 SQL)
 *
 * V2 分区迁移后, 月分区由 {@code TaskArchiver#ensureMonthlyPartitions} 每日滚动预建:
 * default 分区无冲突数据时直接 PARTITION OF; 有 (跨月窗口写入) 则走
 * 承接表搬运 + ATTACH 流程, 保证 default 中数据不丢。
 *
 * 表名/日期由调用方内部生成 (asts_task_yyyyMM / 月首日), 无外部输入注入面。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Mapper
public interface TaskPartitionMapper {

    /** 分区表是否已存在 (pg_class) */
    boolean partitionExists(@Param("tableName") String tableName);

    /** default 分区中落入 [start, end) 的行数 */
    int countDefaultRowsInRange(@Param("start") String start, @Param("end") String end);

    /** 直接创建分区 (要求 default 无该范围数据, 否则 PG 报冲突) */
    void createPartition(@Param("tableName") String tableName,
                         @Param("start") String start, @Param("end") String end);

    /** 创建与 asts_task 同结构的普通承接表 (LIKE INCLUDING DEFAULTS) */
    void createStandaloneLike(@Param("tableName") String tableName);

    /** 从 default 分区复制行到承接表 (copy + delete 两步, 由调用方事务保证原子) */
    int copyDefaultRowsTo(@Param("tableName") String tableName,
                          @Param("start") String start, @Param("end") String end);

    /** 删除 default 分区中 [start, end) 的行 (配合 {@link #copyDefaultRowsTo}) */
    int deleteDefaultRowsInRange(@Param("start") String start, @Param("end") String end);

    /** 将承接表 ATTACH 为分区 (父表索引自动在新区补建) */
    void attachPartition(@Param("tableName") String tableName,
                         @Param("start") String start, @Param("end") String end);
}
