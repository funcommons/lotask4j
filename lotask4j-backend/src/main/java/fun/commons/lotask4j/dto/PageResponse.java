package fun.commons.lotask4j.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 分页响应对象
 *
 * @param <T> 数据类型
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
public class PageResponse<T> {

    /**
     * 数据列表
     */
    private List<T> list;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页数量
     */
    private Integer pageSize;

    /**
     * 总页数
     */
    private Integer totalPages;

    public PageResponse() {
    }

    public PageResponse(List<T> list, Long total, Integer page, Integer pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
    }

    public static <T> PageResponse<T> of(List<T> list, Long total, Integer page, Integer pageSize) {
        return new PageResponse<>(list, total, page, pageSize);
    }
}
