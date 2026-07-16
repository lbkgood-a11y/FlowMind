package com.triobase.service.openapi.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTest {

    @Test
    void registersTenantOptimisticLockAndPaginationInterceptorsInOrder() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .hasSize(3)
                .element(0).isInstanceOf(TenantLineInnerInterceptor.class);
        assertThat(interceptor.getInterceptors().get(1))
                .isInstanceOf(OptimisticLockerInnerInterceptor.class);
        assertThat(interceptor.getInterceptors().get(2))
                .isInstanceOf(PaginationInnerInterceptor.class);
    }
}
