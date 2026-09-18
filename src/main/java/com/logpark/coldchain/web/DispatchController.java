package com.logpark.coldchain.web;

import com.logpark.coldchain.service.BoardService;
import com.logpark.coldchain.service.DispatchService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** 园区调度：异常闭环定责、货主改仓、限电、改约、闸口排队放行、看板。 */
@RestController
@RequestMapping("/api")
public class DispatchController {

    private final DispatchService dispatch;
    private final BoardService board;

    public DispatchController(DispatchService dispatch, BoardService board) {
        this.dispatch = dispatch;
        this.board = board;
    }

    @PostMapping("/dispatch/trips/{code}/incidents/{incidentId}/resolve")
    public Map<String, Object> resolve(@PathVariable String code, @PathVariable long incidentId,
                                       @RequestBody Map<String, Object> body, Principal principal) {
        return dispatch.resolveIncident(code, incidentId,
                String.valueOf(body.get("responsibility")),
                String.valueOf(body.getOrDefault("resolution", "")), principal.getName());
    }

    @PostMapping("/dispatch/trips/{code}/warehouse-change")
    public Map<String, Object> changeWarehouse(@PathVariable String code,
                                               @RequestBody Map<String, Object> body, Principal principal) {
        return dispatch.changeWarehouse(code,
                ((Number) body.get("ownerId")).longValue(),
                String.valueOf(body.get("newWarehouse")), principal.getName());
    }

    @PostMapping("/dispatch/trips/{code}/rebook")
    public Map<String, Object> rebook(@PathVariable String code,
                                      @RequestBody Map<String, Object> body, Principal principal) {
        return dispatch.rebook(code, String.valueOf(body.get("requestedAt")),
                (Boolean) body.get("urgent"), principal.getName());
    }

    @PostMapping("/dispatch/trips/{code}/admit-queue")
    public Map<String, Object> admitQueue(@PathVariable String code, Principal principal) {
        return dispatch.admitFromQueue(code, principal.getName());
    }

    @PostMapping("/dispatch/power-limit")
    public Map<String, Object> powerLimit(@RequestBody Map<String, Object> body, Principal principal) {
        return dispatch.setPowerLimit(Boolean.parseBoolean(String.valueOf(body.get("limited"))),
                principal.getName());
    }

    @GetMapping("/dispatch/queue")
    public List<Map<String, Object>> queue() {
        return dispatch.queue();
    }

    @GetMapping("/board/overview")
    public Map<String, Object> board() {
        return board.board();
    }

    @GetMapping("/board/incidents")
    public List<Map<String, Object>> openIncidents() {
        return board.openIncidents();
    }
}
