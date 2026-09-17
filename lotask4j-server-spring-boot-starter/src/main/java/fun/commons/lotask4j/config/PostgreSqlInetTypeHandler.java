package fun.commons.lotask4j.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.*;

/**
 * PostgreSQL INET 类型处理器
 *
 * 用于 Java String 与 PostgreSQL INET 类型之间的转换
 * 解决 MyBatis Plus updateById() 自动生成 SQL 时的类型转换问题
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@MappedTypes(String.class)
public class PostgreSqlInetTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        // 将 String 转换为 PostgreSQL INET 类型
        PGobject pGobject = new PGobject();
        pGobject.setType("inet");
        pGobject.setValue(parameter);
        ps.setObject(i, pGobject);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object object = rs.getObject(columnName);
        return object == null ? null : object.toString();
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object object = rs.getObject(columnIndex);
        return object == null ? null : object.toString();
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object object = cs.getObject(columnIndex);
        return object == null ? null : object.toString();
    }
}
