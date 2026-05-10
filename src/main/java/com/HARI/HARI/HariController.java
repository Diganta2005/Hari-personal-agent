package com.HARI.HARI;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hari")
public class HariController {

    private final AgentService agentService;

    public HariController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return new ChatResponse(agentService.reply(request.message()));
    }
}
