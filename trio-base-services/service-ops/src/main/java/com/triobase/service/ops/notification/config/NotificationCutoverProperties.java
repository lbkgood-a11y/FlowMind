package com.triobase.service.ops.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 通知中心兼容窗口的全局开关和租户试点名单。
 *
 * <p>v2 开关默认关闭，租户还必须出现在对应名单中才会生效。legacy 写默认开启，并可在完成
 * 对账的试点租户上单独停止。关闭全局开关是事故回滚的 kill switch；关闭后保留已经写入的 v2
 * 证据，禁止以回滚为由清理新表。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "triobase.notification.cutover")
public class NotificationCutoverProperties {
    private boolean dualWriteEnabled;
    private boolean readV2Enabled;
    private boolean legacyWriteEnabled = true;
    private Set<String> dualWriteTenants = new HashSet<>();
    private Set<String> readV2Tenants = new HashSet<>();
    private Set<String> legacyWriteDisabledTenants = new HashSet<>();
}
