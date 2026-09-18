package com.logpark.coldchain.web;

import com.logpark.coldchain.service.CustomerService;
import com.logpark.coldchain.service.SignoffService;
import com.logpark.coldchain.service.TraceService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** 客户客服：异常的可理解解释、客户签收、运输质量追溯。 */
@RestController
@RequestMapping("/api")
public class CustomerController {

    private final CustomerService customer;
    private final SignoffService signoff;
    private final TraceService trace;

    public CustomerController(CustomerService customer, SignoffService signoff, TraceService trace) {
        this.customer = customer;
        this.signoff = signoff;
        this.trace = trace;
    }

    @GetMapping("/cs/trips/{code}/explanation")
    public Map<String, Object> explain(@PathVariable String code) {
        return customer.explain(code);
    }

    @PostMapping("/signoff/trips/{code}")
    public Map<String, Object> signoff(@PathVariable String code,
                                       @RequestBody Map<String, Object> body, Principal principal) {
        return signoff.signoff(code, body, principal.getName());
    }

    @GetMapping("/trace/trips/{code}")
    public Map<String, Object> traceTrip(@PathVariable String code) {
        return trace.trace(code);
    }

    @GetMapping("/trace/owners/{ownerId}/trips")
    public List<Map<String, Object>> traceOwner(@PathVariable long ownerId) {
        return trace.byOwner(ownerId);
    }
}
