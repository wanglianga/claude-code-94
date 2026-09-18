package com.logpark.coldchain.web;

import com.logpark.coldchain.service.DockQcService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * 月台接车（调度）、温度原始数据上传（承运商）、卸货登记与质检抽样（质检）。
 */
@RestController
@RequestMapping("/api")
public class DockQcController {

    private final DockQcService svc;

    public DockQcController(DockQcService svc) {
        this.svc = svc;
    }

    @PostMapping("/dock/trips/{code}/receive")
    public Map<String, Object> receive(@PathVariable String code, Principal principal) {
        return svc.receiveAtDock(code, principal.getName());
    }

    @PostMapping("/dock/trips/{code}/temperature")
    public Map<String, Object> temperature(@PathVariable String code,
                                           @RequestBody Map<String, Object> body, Principal principal) {
        return svc.uploadTemperature(code, body, principal.getName());
    }

    @PostMapping("/dock/trips/{code}/unload")
    public Map<String, Object> unload(@PathVariable String code,
                                      @RequestBody Map<String, Object> body, Principal principal) {
        return svc.completeUnload(code, body, principal.getName());
    }

    @PostMapping("/qc/trips/{code}/sample")
    public Map<String, Object> sample(@PathVariable String code,
                                      @RequestBody Map<String, Object> body, Principal principal) {
        return svc.qcSample(code, body, principal.getName());
    }
}
