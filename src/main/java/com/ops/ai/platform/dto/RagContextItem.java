package com.ops.ai.platform.dto;

import lombok.Data;

/**
 * RAG 检索到的历史案例上下文，用于注入诊断 Prompt。
 */
@Data
public class RagContextItem {

    /** 知识条目标题 */
    private String title;

    /** 历史告警/工单来源类型 */
    private String sourceType;

    /** AI 根因分析 */
    private String rootCause;

    /** AI 修复建议 */
    private String suggestedFix;

    /** 经验总结 */
    private String experienceSummary;

    /** 相关度得分 */
    private double score;
}
