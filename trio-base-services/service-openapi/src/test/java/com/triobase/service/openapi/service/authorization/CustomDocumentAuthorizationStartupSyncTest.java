package com.triobase.service.openapi.service.authorization;

import com.triobase.common.dto.authz.CustomDocumentAuthorizationManifest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CustomDocumentAuthorizationStartupSyncTest {

    @Test
    void runSynchronizesReferenceContractManifest() {
        CustomDocumentAuthorizationSyncClient syncClient = mock(CustomDocumentAuthorizationSyncClient.class);
        CustomDocumentAuthorizationStartupSync startupSync =
                new CustomDocumentAuthorizationStartupSync(syncClient, "tenant-a");

        startupSync.run(null);

        ArgumentCaptor<CustomDocumentAuthorizationManifest> captor =
                ArgumentCaptor.forClass(CustomDocumentAuthorizationManifest.class);
        verify(syncClient).sync(captor.capture());
        assertEquals("tenant-a", captor.getValue().getTenantId());
        assertEquals("service-openapi", captor.getValue().getServiceName());
        assertEquals("CUSTOM_DOC:CONTRACT", captor.getValue().getDocuments().getFirst().getCode());
    }
}
