package com.ops.ai.platform.dto;

import lombok.Data;

import java.util.List;

@Data
public class CurrentUserResponse {

    private Long id;

    private String username;

    private String email;

    private Integer status;

    private List<String> roles;

    private List<String> permissions;
}
