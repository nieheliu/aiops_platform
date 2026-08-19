package com.ops.ai.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_knowledge")
public class OpsKnowledge {

    @TableId
    private Long id;

    private String title;

    private String contentMd;

    private String tags;

    private Integer syncEsStatus;

    private Long sourceAlertId;

    private Long sourceTicketId;

    private Long sourceDiagnosisId;

    private String lifecycleStatus;

    private Integer version;

    private String component;

    private String entrySource;

    private Long createdBy;

    private Long updatedBy;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
