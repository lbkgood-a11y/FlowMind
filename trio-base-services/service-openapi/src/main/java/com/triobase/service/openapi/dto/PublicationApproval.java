package com.triobase.service.openapi.dto;

public record PublicationApproval(
        boolean assetOwnerApproved,
        boolean platformAdministratorApproved,
        boolean startNewCompatibilityLine) {

    public static PublicationApproval none() {
        return new PublicationApproval(false, false, false);
    }

    public boolean dualApproved() {
        return assetOwnerApproved && platformAdministratorApproved;
    }
}
