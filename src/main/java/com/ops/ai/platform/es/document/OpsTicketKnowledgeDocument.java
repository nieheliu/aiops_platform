package com.ops.ai.platform.es.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "ops_ticket_knowledge")
public class OpsTicketKnowledgeDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long ticketId;

    @Field(type = FieldType.Long)
    private Long alertId;

    @Field(type = FieldType.Long)
    private Long diagnosisId;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Text)
    private String aiRootCause;

    @Field(type = FieldType.Text)
    private String aiSuggestedFix;

    @Field(type = FieldType.Text)
    private String experienceSummary;

    @Field(type = FieldType.Long)
    private Long handlerUserId;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime resolvedAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime indexedAt;

    @Field(type = FieldType.Keyword)
    private String sourceType;

    @Field(type = FieldType.Keyword)
    private String aiModel;

    @Field(type = FieldType.Long)
    private Long knowledgeId;

    @Field(type = FieldType.Keyword)
    private String lifecycleStatus;

    @Field(type = FieldType.Integer)
    private Integer version;

    @Field(type = FieldType.Keyword)
    private String component;

    @Field(type = FieldType.Text)
    private String contentMd;

    @Field(type = FieldType.Keyword)
    private String createdByName;

    @Field(type = FieldType.Keyword)
    private String updatedByName;

    /** RAG 向量检索用 embedding（nomic-embed-text，768 维） */
    @Field(type = FieldType.Dense_Vector, dims = 768, similarity = "cosine")
    private float[] embedding;
}
