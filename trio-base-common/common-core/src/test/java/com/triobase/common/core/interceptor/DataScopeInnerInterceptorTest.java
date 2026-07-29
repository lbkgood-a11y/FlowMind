package com.triobase.common.core.interceptor;

import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.context.DataScopeContextHolder;
import com.triobase.common.core.exception.BizException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataScopeInnerInterceptorTest {

    @AfterEach
    void clearScope() {
        DataScopeContextHolder.clear();
    }

    @Test
    void parseFailureDeniesScopedQuery() {
        DataScopeContextHolder.set(DataScope.restrictive("user-1", "FORM:EXPENSE", "VIEW"));
        MappedStatement statement = mock(MappedStatement.class);
        BoundSql boundSql = mock(BoundSql.class);
        when(statement.getId()).thenReturn("FormMapper.selectPage");
        when(boundSql.getSql()).thenReturn("this is not valid sql");

        DataScopeInnerInterceptor interceptor = new DataScopeInnerInterceptor();

        assertThrows(BizException.class, () -> interceptor.beforeQuery(
                null, statement, null, null, null, boundSql));
    }
}
