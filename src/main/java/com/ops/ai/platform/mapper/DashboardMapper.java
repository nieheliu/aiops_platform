package com.ops.ai.platform.mapper;

import com.ops.ai.platform.dto.StatItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM ops_alert WHERE trigger_time >= #{startTime} AND trigger_time < #{endTime}")
    Long countTodayAlerts(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT COUNT(*) FROM ops_ticket WHERE status IN (0, 1)")
    Long countPendingTickets();

    @Select("SELECT COUNT(*) FROM ai_diagnosis WHERE create_time >= #{startTime} AND create_time < #{endTime}")
    Long countTodayDiagnoses(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT COUNT(*) FROM ops_knowledge")
    Long countKnowledge();

    @Select("SELECT CASE status WHEN 0 THEN '待处理' WHEN 1 THEN '处理中' WHEN 2 THEN '已解决' ELSE '未知' END AS name, COUNT(*) AS value FROM ops_ticket GROUP BY status ORDER BY status")
    List<StatItem> countTicketStatusStats();

    @Select("SELECT CASE severity WHEN 0 THEN '提示' WHEN 1 THEN '一般' WHEN 2 THEN '严重' WHEN 3 THEN '致命' ELSE '未知' END AS name, COUNT(*) AS value FROM ops_alert GROUP BY severity ORDER BY severity")
    List<StatItem> countAlertSeverityStats();

    @Select("SELECT DATE_FORMAT(trigger_time, '%m-%d') AS name, COUNT(*) AS value FROM ops_alert WHERE trigger_time >= #{startTime} GROUP BY DATE_FORMAT(trigger_time, '%m-%d') ORDER BY MIN(trigger_time)")
    List<StatItem> countAlertTrend(@Param("startTime") LocalDateTime startTime);
}
