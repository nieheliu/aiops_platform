package com.ops.ai.platform.service;

import com.ops.ai.platform.dto.DashboardSummaryResponse;

public interface DashboardService {

    DashboardSummaryResponse getSummary();

    DashboardSummaryResponse getSummary(boolean forceRefresh);
}
