package com.ops.ai.platform.service;

import com.ops.ai.platform.dto.TicketKnowledgeSearchResponse;
import com.ops.ai.platform.dto.TicketResolveRequest;

public interface TicketKnowledgeService {

    Boolean startTicket(Long ticketId);

    Boolean resolveTicket(Long ticketId, TicketResolveRequest request);

    Boolean closeTicket(Long ticketId);

    TicketKnowledgeSearchResponse search(String keyword, int page, int size);
}
