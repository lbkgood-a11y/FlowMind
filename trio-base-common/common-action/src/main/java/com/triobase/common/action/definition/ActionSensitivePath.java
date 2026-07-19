package com.triobase.common.action.definition;

import lombok.Data;

@Data
public class ActionSensitivePath {
    private String path;
    private String maskStrategy = "REDACT";

    public static ActionSensitivePath of(String path) {
        ActionSensitivePath sensitivePath = new ActionSensitivePath();
        sensitivePath.setPath(path);
        return sensitivePath;
    }
}
