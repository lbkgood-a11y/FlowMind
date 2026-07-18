package com.triobase.service.openapi.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresLocalDateTimeTypeHandlerTest {

    private final PostgresLocalDateTimeTypeHandler handler = new PostgresLocalDateTimeTypeHandler();

    @Test
    void readsTimestampWithTimezoneAsLocalDateTime() throws Exception {
        LocalDateTime value = LocalDateTime.of(2026, 7, 18, 12, 0, 1);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(value));

        assertThat(handler.getNullableResult(resultSet, "created_at")).isEqualTo(value);
    }

    @Test
    void writesLocalDateTimeAsTimestamp() throws Exception {
        LocalDateTime value = LocalDateTime.of(2026, 7, 18, 12, 5);
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 1, value, null);

        verify(statement).setTimestamp(1, Timestamp.valueOf(value));
    }

    @Test
    void returnsNullForMissingTimestamp() throws Exception {
        CallableStatement statement = mock(CallableStatement.class);
        when(statement.getTimestamp(1)).thenReturn(null);

        assertThat(handler.getNullableResult(statement, 1)).isNull();
    }
}
