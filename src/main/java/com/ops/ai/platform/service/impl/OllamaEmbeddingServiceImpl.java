package com.ops.ai.platform.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.ai.platform.service.OllamaEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaEmbeddingServiceImpl implements OllamaEmbeddingService {

    private static final int TIMEOUT_SECONDS = 60;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${aiops.embedding.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${aiops.embedding.model:nomic-embed-text}")
    private String model;

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            String payload = objectMapper.writeValueAsString(
                    java.util.Map.of("model", model, "input", text));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embed"))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Ollama embed failed, status={}, body={}", response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddings = root.get("embeddings");
            if (embeddings == null || !embeddings.isArray() || embeddings.isEmpty()) {
                log.warn("Ollama embed response has no embeddings: {}", response.body());
                return null;
            }
            JsonNode vector = embeddings.get(0);
            float[] result = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                result[i] = (float) vector.get(i).asDouble();
            }
            return result;
        } catch (Exception e) {
            log.warn("Ollama embed failed, text={}", truncate(text, 50), e);
            return null;
        }
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
