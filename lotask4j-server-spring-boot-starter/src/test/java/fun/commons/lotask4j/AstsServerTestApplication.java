package fun.commons.lotask4j;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * starter 测试夹具 — 取代原 lotask4j-backend AstsApplication 的测试根角色。
 *
 * <p>刻意用 {@code @SpringBootConfiguration + @EnableAutoConfiguration} 裸组合而非
 * {@code @SpringBootApplication}: 后者默认组件扫描本包会连带扫描 starter 全部业务包,
 * 与 AstsServerAutoConfiguration 的 @ComponentScan 双注册 (thmp 家族教训)。业务 bean
 * 只经 imports 文件 (引用即嵌入) 到位 — 测试与真实宿主走完全相同的装配路径。
 *
 * <p>置于测试根包: 其余 @SpringBootTest 未显式给 classes 时按包向上搜索即可命中,
 * 与原 AstsApplication 解析方式一致。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class AstsServerTestApplication {
}
