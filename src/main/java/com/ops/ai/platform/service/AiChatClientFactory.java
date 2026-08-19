package com.ops.ai.platform.service;

import com.ops.ai.platform.config.AiOpsModelProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiChatClientFactory {

    public ChatClient createClient(AiOpsModelProperties.ModelDefinition definition) {
        String apiKey = resolveApiKey(definition);
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("模型 " + definition.getName() + " 的 API Key 未配置，请检查 local.env");
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(definition.getBaseUrl())
                .apiKey(apiKey)
                .completionsPath("/chat/completions")
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model(definition.getModel())
                        .build())
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private String resolveApiKey(AiOpsModelProperties.ModelDefinition definition) {
        // 本地 Ollama 模型不需要 API Key，使用占位串即可
        if ("ollama".equalsIgnoreCase(definition.getProvider())) {
            return "ollama";
        }
        String envName = definition.getApiKeyEnv();
        if (!StringUtils.hasText(envName)) {
            return null;
        }
        return System.getenv(envName);
    }
}
