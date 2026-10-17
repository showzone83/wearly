package app.wearly.common.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** PostgreSQL JSONB ↔ Map&lt;String, Object&gt;. 매퍼 XML에서 typeHandler로 지정해 쓴다. */
@MappedTypes(Map.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = false)
public class JsonbMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, JSON.writeValueAsString(parameter), Types.OTHER);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static Map<String, Object> parse(String json) {
        return json == null ? null : JSON.readValue(json, MAP);
    }
}
