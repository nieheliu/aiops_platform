package com.ops.ai.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.ops.ai.platform.mapper")
@SpringBootApplication
public class OpsAiPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsAiPlatformApplication.class, args);
    }
}
