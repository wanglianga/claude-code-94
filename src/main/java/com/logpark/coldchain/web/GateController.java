package com.logpark.coldchain.web;

import com.logpark.coldchain.service.GateService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** 闸口核验：预约/车牌/封签，晚到/拥堵/限电/证件异常则排队。 */
@RestController
@RequestMapping("/api/gate")
public class GateController {

    private final GateService gate;

    public GateController(GateService gate) {
        this.gate = gate;
    }

    @PostMapping("/checkin")
    public Map<String, Object> checkIn(@RequestBody Map<String, Object> body, Principal principal) {
        return gate.checkIn(body, principal.getName());
    }

    @GetMapping("/queue")
    public List<Map<String, Object>> queue() {
        return gate.queue();
    }
}
