package com.ops.ai.platform.controller;

import com.ops.ai.platform.dto.AiModelOption;
import com.ops.ai.platform.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ai-models")
public class AiModelController {

    private final AiModelService aiModelService;

    @GetMapping
    public List<AiModelOption> listAll() {
        return aiModelService.listAll();
    }

    @GetMapping("/available")
    public List<AiModelOption> listAvailable(@RequestParam(required = false) Long alertId,
                                               @RequestParam(required = false) Long ticketId) {
        return aiModelService.listAvailable(alertId, ticketId);
    }
}
