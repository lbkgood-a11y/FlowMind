package com.triobase.service.lowcode.service;

import com.triobase.common.core.auth.FieldEnforcementManifest;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Typed declaration for dynamic form fields; concrete field keys are supplied by each form schema. */
@Component
public class LowcodeFieldEnforcementDeclaration {

    public FieldEnforcementManifest manifest() {
        return new FieldEnforcementManifest(
                "service-lowcode", "*", "LOWCODE_FORM", Set.of(),
                true, true, true,
                Set.of("LIST", "DETAIL", "SAVE", "SUBMIT", "OWNER_ACTION"));
    }
}
