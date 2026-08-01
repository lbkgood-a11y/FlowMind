package com.triobase.service.auth.service;

import com.triobase.common.core.auth.FieldEnforcementManifest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerFieldEnforcementManifestTest {

    @Test
    void everyExistingFieldBearingResourceHasReadyOwnerCoverage() {
        List<FieldEnforcementManifest> manifests = AuthorizationRegistryService.ownerFieldManifests();
        Set<String> covered = manifests.stream()
                .peek(manifest -> {
                    assertThat(manifest.ownerService()).isNotBlank();
                    assertThat(manifest.readHideEnforced()).isTrue();
                    assertThat(manifest.readMaskEnforced()).isTrue();
                    assertThat(manifest.writeDenyEnforced()).isTrue();
                    assertThat(manifest.coveredBoundaries()).isNotEmpty();
                })
                .map(manifest -> manifest.ownerService() + ":" + manifest.resourceCode())
                .collect(Collectors.toSet());

        assertThat(covered).containsExactlyInAnyOrder(
                "service-auth:USER", "service-org:ORG_UNIT",
                "service-lowcode:*", "service-api-runtime:CUSTOM_DOC:CONTRACT");
    }
}
