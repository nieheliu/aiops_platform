package com.ops.ai.platform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_ticket_log")
public class OpsTicketLog {

    @TableId
    private Long id;

    private Long ticketId;

    private Long operatorId;

    private String action;

    private String remark;

    private LocalDateTime operateTime;
}
