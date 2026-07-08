package com.triobase.common.temporal.base;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity 幂等基类 — 铁律 7：所有 Activity 必须实现业务级幂等控制。
 * 子类实现 doExecute 方法，本基类负责提供幂等键（idempotencyKey）作为前置校验锚点。
 */
@ActivityInterface
public interface BaseActivity {

    @ActivityMethod
    String getIdempotencyKey();
}
