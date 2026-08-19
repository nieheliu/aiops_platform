package com.ops.ai.platform.service;

import com.ops.ai.platform.dto.RagContextItem;

import java.util.List;

/**
 * RAG 检索服务：诊断前从知识库检索历史相似案例。
 */
public interface RagRetrievalService {

    /**
     * 根据关键词从知识库检索相关历史案例。
     *
     * @param keyword 检索关键词（告警名称 + 工单描述拼接）
     * @param topK    返回条数上限
     * @return 相关案例列表；检索失败或 ES 不可用时返回空列表（调用方静默降级）
     */
    List<RagContextItem> retrieveRelated(String keyword, int topK);
}
