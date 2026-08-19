package com.ops.ai.platform.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiOpsModelProperties.class)
public class AiOpsModelsConfiguration {
}
