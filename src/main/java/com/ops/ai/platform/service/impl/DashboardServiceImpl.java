package com.ops.ai.platform.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.ai.platform.dto.DashboardSummaryResponse;
import com.ops.ai.platform.dto.StatItem;
import com.ops.ai.platform.mapper.DashboardMapper;
import com.ops.ai.platform.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final String DASHBOARD_SUMMARY_KEY = "aiops:dashboard:summary";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final DashboardMapper dashboardMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public DashboardSummaryResponse getSummary() {
        DashboardSummaryResponse cached = getFromCache();
        if (cached != null) {
            cached.setCacheHit(true);
            cached.setExpireSeconds(CACHE_TTL.toSeconds());
            return cached;
        }

        DashboardSummaryResponse summary = buildSummaryFromDatabase();
        setToCache(summary);
        return summary;
    }

    private DashboardSummaryResponse getFromCache() {
        try {
            String json = stringRedisTemplate.opsForValue().get(DASHBOARD_SUMMARY_KEY);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, DashboardSummaryResponse.class);
        } catch (Exception e) {
            log.warn("Read dashboard summary cache failed", e);
            return null;
        }
    }

    private void setToCache(DashboardSummaryResponse summary) {
        try {
            stringRedisTemplate.opsForValue().set(DASHBOARD_SUMMARY_KEY, objectMapper.writeValueAsString(summary), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Write dashboard summary cache failed", e);
        }
    }

    private DashboardSummaryResponse buildSummaryFromDatabase() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime trendStartTime = today.minusDays(6).atStartOfDay();

        DashboardSummaryResponse summary = new DashboardSummaryResponse();
        summary.setTodayAlertCount(nullToZero(dashboardMapper.countTodayAlerts(startOfDay, startOfTomorrow)));
        summary.setPendingTicketCount(nullToZero(dashboardMapper.countPendingTickets()));
        summary.setTodayDiagnosisCount(nullToZero(dashboardMapper.countTodayDiagnoses(startOfDay, startOfTomorrow)));
        summary.setKnowledgeCount(nullToZero(dashboardMapper.countKnowledge()));
        summary.setTicketStatusStats(dashboardMapper.countTicketStatusStats());
        summary.setAlertSeverityStats(dashboardMapper.countAlertSeverityStats());
        summary.setAlertTrend(fillLastSevenDaysTrend(dashboardMapper.countAlertTrend(trendStartTime)));
        summary.setCacheHit(false);
        summary.setGeneratedAt(LocalDateTime.now());
        summary.setExpireSeconds(CACHE_TTL.toSeconds());
        return summary;
    }

    private Long nullToZero(Long value) {
        return Optional.ofNullable(value).orElse(0L);
    }

    private List<StatItem> fillLastSevenDaysTrend(List<StatItem> dbTrend) {
        Map<String, Long> counter = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            counter.put(String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth()), 0L);
        }
        for (StatItem item : dbTrend) {
            if (counter.containsKey(item.getName())) {
                counter.put(item.getName(), nullToZero(item.getValue()));
            }
        }
        List<StatItem> result = new ArrayList<>();
        counter.forEach((name, value) -> result.add(new StatItem(name, value)));
        return result;
    }
}
