package com.triobase.common.temporal.base;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity 幂等身份契约——铁律 7。
 *
 * <p>Temporal 的 Activity ID 不能替代业务幂等键；重试、Workflow 重建或人工补偿可能产生
 * 不同执行身份。实现必须返回由业务事实组成的稳定键，并以数据库唯一约束、状态前置校验或
 * 分布式锁真正执行去重。</p>
 */
@ActivityInterface
public interface BaseActivity {

    @ActivityMethod
    String getIdempotencyKey();
}
