package fun.commons.lotask4j.demo.config;

import fun.commons.lotask4j.config.PostgreSqlInetTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostgreSqlInetTypeHandler 单元测试（String ↔ PG INET 转换）
 */
@DisplayName("PostgreSqlInetTypeHandler 单元测试")
class PostgreSqlInetTypeHandlerTest {

    private final PostgreSqlInetTypeHandler handler = new PostgreSqlInetTypeHandler();

    @Test
    @DisplayName("setNonNullParameter: 写入 inet 类型 PGobject")
    void setNonNullParameter() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);

        handler.setNonNullParameter(ps, 1, "10.0.0.1", JdbcType.OTHER);

        verify(ps).setObject(eq(1), any(PGobject.class));
    }

    @Test
    @DisplayName("getNullableResult(列名): null → null; 非 null → toString")
    void getNullableResult_byName() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        PGobject inet = new PGobject();
        inet.setType("inet");
        inet.setValue("192.168.1.1");
        when(rs.getObject("ip")).thenReturn(inet, (Object) null);

        assertThat(handler.getNullableResult(rs, "ip")).isEqualTo("192.168.1.1");
        assertThat(handler.getNullableResult(rs, "ip")).isNull();
    }

    @Test
    @DisplayName("getNullableResult(列下标): null → null; 非 null → toString")
    void getNullableResult_byIndex() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        PGobject inet = new PGobject();
        inet.setType("inet");
        inet.setValue("::1");
        when(rs.getObject(2)).thenReturn(inet, (Object) null);

        assertThat(handler.getNullableResult(rs, 2)).isEqualTo("::1");
        assertThat(handler.getNullableResult(rs, 2)).isNull();
    }

    @Test
    @DisplayName("getNullableResult(CallableStatement): null → null; 非 null → toString")
    void getNullableResult_callable() throws SQLException {
        CallableStatement cs = mock(CallableStatement.class);
        PGobject inet = new PGobject();
        inet.setType("inet");
        inet.setValue("10.1.2.3");
        when(cs.getObject(3)).thenReturn(inet, (Object) null);

        assertThat(handler.getNullableResult(cs, 3)).isEqualTo("10.1.2.3");
        assertThat(handler.getNullableResult(cs, 3)).isNull();
    }
}
