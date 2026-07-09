package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateUserRequest {
    private String password;
    private String email;
    private String phone;
    private Integer status;
    private List<String> roleIds;
}
