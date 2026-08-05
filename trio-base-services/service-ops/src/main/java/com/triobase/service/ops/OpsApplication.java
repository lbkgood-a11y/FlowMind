package com.triobase.service.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
/**
 * 运营服务启动入口，承载公告、消息中心及其内嵌通知 Temporal Worker。
 *
 * <p>Worker 与本应用共享生命周期，禁止将通知 Activity 拆成无业务上下文的通用 Worker。</p>
 */
public class OpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsApplication.class, args);
    }
}
