package fun.commons.lotask4j.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.lotask4j.entity.AstTaskExecutionEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务执行事件 Mapper (P1-3)。
 *
 * append-only — 不应被 UPDATE / DELETE。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Mapper
public interface AstTaskExecutionEventMapper extends BaseMapper<AstTaskExecutionEvent> {

    /**
     * 按 taskId 倒序查询事件。
     */
    List<AstTaskExecutionEvent> selectByTaskIdOrderByCreatedAtDesc(@Param("taskId") Long taskId);

    /**
     * 按 taskId + limit 倒序查询 (前端滚动加载用)。
     */
    List<AstTaskExecutionEvent> selectByTaskIdLimit(@Param("taskId") Long taskId,
                                                      @Param("limit") int limit);

    /**
     * 显式插入事件 (处理 JSONB 字段)。
     */
    int insertDefault(@Param("event") AstTaskExecutionEvent event,
                      @Param("detailJson") String detailJson);
}
