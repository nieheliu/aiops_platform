package com.ops.ai.platform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_knowledge_audit_log")
public class OpsKnowledgeAuditLog {

    @TableId
    private Long id;

    private Long knowledgeId;

    private String action;

    private Long operatorId;

    private String operatorName;

    private String fromStatus;

    private String toStatus;

    private Integer version;

    private String remark;

    private LocalDateTime operateTime;
}
