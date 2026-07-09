package com.triobase.service.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MenuRouteResponse {
    private String name;
    private String path;
    private String component;
    private String redirect;
    private String type;
    private String authCode;
    private Map<String, Object> meta = new LinkedHashMap<>();
    private List<MenuRouteResponse> children = new ArrayList<>();
}
