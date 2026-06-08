package com.ops.ai.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_alert")
public class OpsAlert {

    @TableId
    private Long id;

    private String alertName;

    private Integer severity;

    private String instanceIp;

    private String rawPayload;

    private LocalDateTime triggerTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
