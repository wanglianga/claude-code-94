package com.logpark.coldchain.web;

import com.logpark.coldchain.service.DispositionService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/** 处置决定（放行/隔离/降级/拒收）与责任结算。 */
@RestController
@RequestMapping("/api")
public class DispositionController {

    private final DispositionService svc;

    public DispositionController(DispositionService svc) {
        this.svc = svc;
    }

    @PostMapping("/disposition/trips/{code}/decide")
    public Map<String, Object> decide(@PathVariable String code,
                                      @RequestBody Map<String, Object> body, Principal principal) {
        return svc.decide(code, body, principal.getName());
    }

    @GetMapping("/settle/trips/{code}/preview")
    public Map<String, Object> preview(@PathVariable String code) {
        return svc.previewSettlement(code);
    }

    @PostMapping("/settle/trips/{code}/confirm")
    public Map<String, Object> confirm(@PathVariable String code,
                                       @RequestBody(required = false) Map<String, Object> body,
                                       Principal principal) {
        String override = body == null ? null : (String) body.get("liabilityPartyOverride");
        return svc.confirmSettlement(code, override, principal.getName());
    }
}
