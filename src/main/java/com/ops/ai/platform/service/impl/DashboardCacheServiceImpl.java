package com.ops.ai.platform.service.impl;

import com.ops.ai.platform.service.DashboardCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardCacheServiceImpl implements DashboardCacheService {

    private static final String DASHBOARD_SUMMARY_KEY = "aiops:dashboard:summary";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void evictSummary() {
        try {
            stringRedisTemplate.delete(DASHBOARD_SUMMARY_KEY);
        } catch (Exception e) {
            log.warn("Evict dashboard summary cache failed", e);
        }
    }
}
