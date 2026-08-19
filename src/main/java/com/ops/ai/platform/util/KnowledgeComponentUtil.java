package com.ops.ai.platform.util;

import org.springframework.util.StringUtils;

public final class KnowledgeComponentUtil {

    private KnowledgeComponentUtil() {
    }

    public static String inferComponent(String... texts) {
        String merged = String.join(" ", texts).toLowerCase();
        if (!StringUtils.hasText(merged)) {
            return "other";
        }
        if (containsAny(merged, "mysql", "mariadb")) return "mysql";
        if (containsAny(merged, "redis")) return "redis";
        if (containsAny(merged, "rabbitmq", "mq")) return "rabbitmq";
        if (containsAny(merged, "elasticsearch", "es")) return "elasticsearch";
        if (containsAny(merged, "cpu", "load")) return "cpu";
        if (containsAny(merged, "memory", "mem", "oom")) return "memory";
        if (containsAny(merged, "disk", "filesystem", "inode")) return "disk";
        if (containsAny(merged, "network", "tcp", "latency")) return "network";
        if (containsAny(merged, "jvm", "gc", "heap")) return "jvm";
        if (containsAny(merged, "nginx", "tomcat", "spring", "application", "app")) return "application";
        return "other";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
