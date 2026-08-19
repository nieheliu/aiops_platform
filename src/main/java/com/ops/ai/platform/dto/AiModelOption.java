package com.ops.ai.platform.dto;

import lombok.Data;

@Data
public class AiModelOption {

    private String id;

    private String name;

    private String provider;

    private String model;

    private boolean free;

    private boolean used;

    private boolean available;
}
