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
}
