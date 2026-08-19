package com.ops.ai.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "aiops")
public class AiOpsModelProperties {

    private String defaultModelId = "deepseek-v4-flash-free";

    private List<ModelDefinition> models = new ArrayList<>();

    @Data
    public static class ModelDefinition {
        private String id;
        private String name;
        private String provider;
        private String baseUrl;
        private String apiKeyEnv;
        private String model;
        private boolean free;
        /** 部分百炼模型仅支持流式调用，如 glm-4.5-air */
        private boolean streamOnly;
    }
}
