package com.ops.ai.platform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId
    private Long userId;

    private Long roleId;

    private LocalDateTime grantTime;
}
