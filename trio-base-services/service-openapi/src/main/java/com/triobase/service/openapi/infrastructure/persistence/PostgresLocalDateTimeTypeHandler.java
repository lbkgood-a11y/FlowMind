package com.triobase.service.openapi.infrastructure.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@MappedTypes(LocalDateTime.class)
@MappedJdbcTypes(value = {JdbcType.TIMESTAMP, JdbcType.TIMESTAMP_WITH_TIMEZONE}, includeNullJdbcType = true)
public class PostgresLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(
            PreparedStatement preparedStatement,
            int index,
            LocalDateTime parameter,
            JdbcType jdbcType) throws SQLException {
        preparedStatement.setTimestamp(index, Timestamp.valueOf(parameter));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return toLocalDateTime(resultSet.getTimestamp(columnName));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return toLocalDateTime(resultSet.getTimestamp(columnIndex));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return toLocalDateTime(statement.getTimestamp(columnIndex));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
