package com.ops.ai.platform.service.impl;

import com.ops.ai.platform.common.KnowledgeLifecycle;
import com.ops.ai.platform.config.RagProperties;
import com.ops.ai.platform.dto.RagContextItem;
import com.ops.ai.platform.es.document.OpsTicketKnowledgeDocument;
import com.ops.ai.platform.service.OllamaEmbeddingService;
import com.ops.ai.platform.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final ElasticsearchOperations elasticsearchOperations;

    private final RagProperties ragProperties;

    private final OllamaEmbeddingService ollamaEmbeddingService;

    @Override
    public List<RagContextItem> retrieveRelated(String keyword, int topK) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        try {
            if (!indexExists()) {
                return List.of();
            }
            int limit = Math.max(1, Math.min(topK, 20));
            String mode = StringUtils.hasText(ragProperties.getMode()) ? ragProperties.getMode() : "hybrid";

            if ("bm25".equalsIgnoreCase(mode)) {
                return toContextItems(bm25Search(keyword, limit));
            }
            // hybrid：BM25 词法召回 + 向量 kNN 语义召回，RRF 融合
            List<ScoredDoc> bm25List = bm25Search(keyword, limit);
            List<ScoredDoc> knnList = knnSearch(keyword, limit);
            List<ScoredDoc> fused = fuseRrf(bm25List, knnList, limit);
            log.info("RAG retrieval: keyword={}, mode=hybrid, bm25={}, knn={}, fused={}",
                    keyword, bm25List.size(), knnList.size(), fused.size());
            return toContextItems(fused);
        } catch (Exception e) {
            log.warn("RAG retrieval failed, degrade to empty context. keyword={}", keyword, e);
            return List.of();
        }
    }

    /** BM25 词法召回（带归档过滤 + minScore 阈值） */
    private List<ScoredDoc> bm25Search(String keyword, int limit) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.must(m -> m.multiMatch(mm -> mm.query(keyword.trim())
                            .fields("title^3", "experienceSummary^2", "contentMd^2",
                                    "description", "aiRootCause", "aiSuggestedFix")));
                    // 排除已归档/废弃，保留 PUBLISHED 及历史数据中未标注 lifecycle 的知识
                    b.filter(f -> f.bool(fb -> fb
                            .mustNot(mn -> mn.term(t -> t.field("lifecycleStatus").value(KnowledgeLifecycle.ARCHIVED)))
                            .mustNot(mn -> mn.term(t -> t.field("lifecycleStatus").value(KnowledgeLifecycle.DEPRECATED)))));
                    return b;
                }))
                .withPageable(PageRequest.of(0, limit))
                .build();
        SearchHits<OpsTicketKnowledgeDocument> hits = elasticsearchOperations.search(
                query, OpsTicketKnowledgeDocument.class);
        List<ScoredDoc> docs = new ArrayList<>();
        for (SearchHit<OpsTicketKnowledgeDocument> hit : hits) {
            if (hit.getScore() < ragProperties.getMinScore()) {
                continue;
            }
            docs.add(toScoredDoc(hit, hit.getScore()));
        }
        return docs;
    }

    /** 向量 kNN 语义召回（Ollama embedding + ES 原生 knn 查询） */
    private List<ScoredDoc> knnSearch(String keyword, int limit) {
        float[] queryVector = ollamaEmbeddingService.embed(keyword);
        if (queryVector == null) {
            log.warn("RAG kNN skip: embedding generation failed");
            return List.of();
        }
        int candidates = Math.max(limit * 5, ragProperties.getKnnCandidates());
        String body = "{\"knn\":{\"field\":\"embedding\",\"query_vector\":"
                + toJsonArray(queryVector) + ",\"k\":" + candidates + ",\"num_candidates\":" + candidates + "}}";
        SearchHits<OpsTicketKnowledgeDocument> hits = elasticsearchOperations.search(
                new StringQuery(body), OpsTicketKnowledgeDocument.class);
        List<ScoredDoc> docs = new ArrayList<>();
        for (SearchHit<OpsTicketKnowledgeDocument> hit : hits) {
            if (docs.size() >= limit) {
                break;
            }
            docs.add(toScoredDoc(hit, hit.getScore()));
        }
        return docs;
    }

    /** RRF 融合：两路结果按各自 rank 累加 1/(k+rank)，取融合分 topK */
    private List<ScoredDoc> fuseRrf(List<ScoredDoc> bm25, List<ScoredDoc> knn, int limit) {
        int k = Math.max(1, ragProperties.getRrfK());
        Map<String, RrfAccumulator> acc = new HashMap<>();
        accumulate(acc, bm25, k);
        accumulate(acc, knn, k);

        return acc.values().stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(limit)
                .map(a -> {
                    ScoredDoc doc = a.doc;
                    return new ScoredDoc(doc.docId, doc.title, doc.sourceType, doc.rootCause,
                            doc.suggestedFix, doc.experienceSummary, (float) a.score);
                })
                .toList();
    }

    private void accumulate(Map<String, RrfAccumulator> acc, List<ScoredDoc> docs, int k) {
        for (int i = 0; i < docs.size(); i++) {
            ScoredDoc doc = docs.get(i);
            RrfAccumulator a = acc.computeIfAbsent(doc.docId, id -> new RrfAccumulator());
            a.score += 1.0 / (k + i + 1);
            if (a.doc == null) {
                a.doc = doc;
            }
        }
    }

    private List<RagContextItem> toContextItems(List<ScoredDoc> docs) {
        List<RagContextItem> items = new ArrayList<>();
        for (ScoredDoc doc : docs) {
            RagContextItem item = new RagContextItem();
            item.setTitle(doc.title);
            item.setSourceType(doc.sourceType);
            item.setRootCause(doc.rootCause);
            item.setSuggestedFix(doc.suggestedFix);
            item.setExperienceSummary(doc.experienceSummary);
            item.setScore(doc.score);
            items.add(item);
        }
        return items;
    }

    private ScoredDoc toScoredDoc(SearchHit<OpsTicketKnowledgeDocument> hit, float score) {
        OpsTicketKnowledgeDocument doc = hit.getContent();
        return new ScoredDoc(hit.getId(), doc.getTitle(), doc.getSourceType(),
                doc.getAiRootCause(), doc.getAiSuggestedFix(), doc.getExperienceSummary(), score);
    }

    private String toJsonArray(float[] vector) {
        StringJoiner joiner = new StringJoiner(",");
        for (float v : vector) {
            joiner.add(Float.toString(v));
        }
        return "[" + joiner + "]";
    }

    private boolean indexExists() {
        try {
            return elasticsearchOperations.indexOps(OpsTicketKnowledgeDocument.class).exists();
        } catch (Exception e) {
            log.warn("Check knowledge index failed for RAG", e);
            return false;
        }
    }

    /** 融合时的内部载体：docId 用于跨路归并，内容取自任一命中 */
    private record ScoredDoc(String docId, String title, String sourceType,
                             String rootCause, String suggestedFix, String experienceSummary, float score) {
    }

    private static class RrfAccumulator {
        double score;
        ScoredDoc doc;
    }
}
