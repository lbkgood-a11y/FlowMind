package com.triobase.service.auth.service;

import com.triobase.service.auth.mapper.AuthVersionMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationVersionServiceTest {

    @Test
    void atomicCompareAndBumpReturnsNextVersion() {
        AuthVersionMapper mapper = mock(AuthVersionMapper.class);
        when(mapper.bumpIfExpected(AuthorizationVersionService.GRANT, 8L)).thenReturn(1);
        AuthorizationVersionService service = new AuthorizationVersionService(mapper);

        assertThat(service.bumpIfExpected(AuthorizationVersionService.GRANT, 8L)).isEqualTo(9L);
        verify(mapper).bumpIfExpected(AuthorizationVersionService.GRANT, 8L);
    }

    @Test
    void atomicCompareAndBumpReportsConflict() {
        AuthVersionMapper mapper = mock(AuthVersionMapper.class);
        when(mapper.bumpIfExpected(AuthorizationVersionService.GRANT, 8L)).thenReturn(0);
        AuthorizationVersionService service = new AuthorizationVersionService(mapper);

        assertThat(service.bumpIfExpected(AuthorizationVersionService.GRANT, 8L)).isEqualTo(-1L);
    }
}
