package com.ops.ai.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_diagnosis")
public class AiDiagnosis {

    @TableId
    private Long id;

    private Long alertId;

    private Long ticketId;

    private String aiModel;

    private String rootCauseAnalysis;

    private String suggestedFix;

    private BigDecimal confidenceScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
