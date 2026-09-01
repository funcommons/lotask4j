package fun.commons.lotask4j.demo.entity;

import fun.commons.lotask4j.entity.WebEmbedConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebEmbedConfig 实体辅助方法测试
 */
@DisplayName("WebEmbedConfig 辅助方法测试")
class WebEmbedConfigTest {

    @Test
    @DisplayName("isOpenMode / isEnabled: 仅 Integer(1) 为真")
    void flags() {
        WebEmbedConfig c = new WebEmbedConfig();
        c.setIsOpen(1);
        c.setIsEnabled(1);
        assertTrue(c.isOpenMode());
        assertTrue(c.isEnabled());

        c.setIsOpen(0);
        c.setIsEnabled(0);
        assertFalse(c.isOpenMode());
        assertFalse(c.isEnabled());

        c.setIsOpen(null);
        c.setIsEnabled(null);
        assertFalse(c.isOpenMode());
        assertFalse(c.isEnabled());
    }
}
