package com.triobase.service.openapi.service.authorization;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "triobase.authorization.custom-doc", name = "sync-enabled", havingValue = "true")
public class CustomDocumentAuthorizationStartupSync implements ApplicationRunner {

    private final CustomDocumentAuthorizationSyncClient syncClient;
    private final String tenantId;

    public CustomDocumentAuthorizationStartupSync(
            CustomDocumentAuthorizationSyncClient syncClient,
            @Value("${triobase.authorization.custom-doc.tenant-id:default}") String tenantId) {
        this.syncClient = syncClient;
        this.tenantId = tenantId;
    }

    @Override
    public void run(ApplicationArguments args) {
        syncClient.sync(ReferenceContractAuthorizationManifest.create(tenantId));
    }
}
