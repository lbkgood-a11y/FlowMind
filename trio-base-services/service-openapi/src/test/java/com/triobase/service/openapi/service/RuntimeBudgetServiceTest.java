package com.triobase.service.openapi.service;
import com.triobase.common.core.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class RuntimeBudgetServiceTest {
 @Mock StringRedisTemplate redis;
 @Test void acquiresAndReleasesTrackedConcurrency(){when(redis.execute(any(RedisScript.class),any(List.class),any(),any())).thenReturn(1L);when(redis.execute(any(RedisScript.class),any(List.class))).thenReturn(0L);RuntimeBudgetService service=new RuntimeBudgetService(redis);assertThatCode(()->{try(var lease=service.acquireWorkflow("tenant","client","route",2)){} }).doesNotThrowAnyException();verify(redis).execute(any(RedisScript.class),any(List.class));}
 @Test void rejectsWhenLimitIsExhausted(){when(redis.execute(any(RedisScript.class),any(List.class),any(),any())).thenReturn(0L);RuntimeBudgetService service=new RuntimeBudgetService(redis);assertThatThrownBy(()->service.acquireRequest("tenant","client","route",1)).isInstanceOf(BizException.class).hasMessage("OPENAPI_RUNTIME_CONCURRENCY_EXHAUSTED");}
 @Test void reservesAndExplicitlyReleasesWorkflowCapacityAcrossProcessLifetime(){when(redis.execute(any(RedisScript.class),any(List.class),any(),any())).thenReturn(1L);when(redis.execute(any(RedisScript.class),any(List.class))).thenReturn(0L);RuntimeBudgetService service=new RuntimeBudgetService(redis);assertThatCode(()->service.reserveWorkflow("tenant","client","route",2)).doesNotThrowAnyException();service.releaseWorkflow("tenant","client","route");verify(redis).execute(any(RedisScript.class),any(List.class));}
}
