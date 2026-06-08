package com.ops.ai.platform.controller;

import com.ops.ai.platform.dto.TicketKnowledgeSearchResponse;
import com.ops.ai.platform.service.TicketKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ticket-knowledge")
public class TicketKnowledgeController {

    private final TicketKnowledgeService ticketKnowledgeService;

    @GetMapping("/search")
    public TicketKnowledgeSearchResponse search(@RequestParam(defaultValue = "") String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return ticketKnowledgeService.search(keyword, page, size);
    }
}
