package com.ops.ai.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG（检索增强生成）配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aiops.rag")
public class RagProperties {

    /** 是否启用 RAG：诊断前检索历史相似案例注入 Prompt */
    private boolean enabled = true;

    /** 召回的历史案例条数 */
    private int topK = 3;

    /** 召回最低相关度阈值（低于该分数不注入，0-1），用于过滤低相关噪声 */
    private double minScore = 0.5;

    /** 注入 Prompt 的案例字段（title/rootCause/suggestedFix/experienceSummary），逗号分隔，可裁剪 Prompt 体积 */
    private String contextFields = "title,rootCause,suggestedFix,experienceSummary";

    /** 检索模式：bm25（词法召回）/ hybrid（BM25 + 向量 kNN 双路召回 + RRF 融合） */
    private String mode = "hybrid";

    /** RRF 融合常数 k（rank 越靠前贡献越大，k 越小排名差异越敏感） */
    private int rrfK = 60;

    /** 向量召回条数（含融合时的候选数） */
    private int knnCandidates = 20;
}
