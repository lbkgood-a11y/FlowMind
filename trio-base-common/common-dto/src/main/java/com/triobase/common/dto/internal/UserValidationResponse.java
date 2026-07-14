package com.triobase.common.dto.internal;

import lombok.Data;

@Data
public class UserValidationResponse {
    private boolean enabled;
    private ResolvedUserDto user;
}
