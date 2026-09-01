package fun.commons.lotask4j.demo.dto;

import fun.commons.lotask4j.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PageResponse DTO 单元测试 — 分页元数据计算
 */
@DisplayName("PageResponse 单元测试")
class PageResponseTest {

    @Test
    @DisplayName("totalPages 向上取整")
    void totalPagesCeil() {
        PageResponse<String> p = PageResponse.of(List.of("a", "b", "c"), 21L, 1, 20);
        assertEquals(2, p.getTotalPages()); // ceil(21/20) = 2
        assertEquals(3, p.getList().size());
        assertEquals(21L, p.getTotal());
        assertEquals(1, p.getPage());
        assertEquals(20, p.getPageSize());
    }

    @Test
    @DisplayName("pageSize 为 0 → totalPages 兜底 0 (不除零)")
    void zeroPageSize() {
        PageResponse<String> p = PageResponse.of(List.of(), 0L, 1, 0);
        assertEquals(0, p.getTotalPages());
    }

    @Test
    @DisplayName("恰好整页 → totalPages 不多算")
    void exactPages() {
        PageResponse<Integer> p = PageResponse.of(List.of(1, 2), 40L, 2, 20);
        assertEquals(2, p.getTotalPages());
    }
}
