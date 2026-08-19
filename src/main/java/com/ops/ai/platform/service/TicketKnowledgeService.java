package com.ops.ai.platform.service;

import com.ops.ai.platform.dto.KnowledgeFacetsResponse;
import com.ops.ai.platform.dto.KnowledgeSearchQuery;
import com.ops.ai.platform.dto.TicketKnowledgeSearchResponse;
import com.ops.ai.platform.dto.TicketResolveRequest;
import com.ops.ai.platform.entity.OpsKnowledge;

public interface TicketKnowledgeService {

    Boolean startTicket(Long ticketId, Long operatorUserId);

    Boolean assignHandler(Long ticketId, Long handlerUserId, Long operatorUserId);

    Boolean resolveTicket(Long ticketId, TicketResolveRequest request, Long operatorUserId);

    Boolean closeTicket(Long ticketId, Long operatorUserId);

    TicketKnowledgeSearchResponse search(KnowledgeSearchQuery query);

    KnowledgeFacetsResponse facets(KnowledgeSearchQuery query);

    long countDocuments();

    Boolean deleteDocument(String documentId);

    String resolveDocumentId(OpsKnowledge knowledge);

    void syncKnowledgeDocument(OpsKnowledge knowledge);

    void removeElasticsearchDocument(String documentId);

    void assertOperatorCanProcess(Long ticketId, Long operatorUserId, boolean allowClaimIfPending);
}
