package com.ops.ai.platform.service;

/**
 * 本地 Ollama embedding 服务：为 RAG 向量检索生成文本向量。
 */
public interface OllamaEmbeddingService {

    /**
     * 将文本转成向量（nomic-embed-text，768 维）。
     *
     * @param text 输入文本
     * @return 向量；生成失败时返回 null（调用方应降级处理，不影响主流程）
     */
    float[] embed(String text);
}
