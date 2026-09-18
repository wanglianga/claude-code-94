package com.coldchain.park.web;

import com.coldchain.park.domain.UserAccount;
import com.coldchain.park.domain.UserRole;
import com.coldchain.park.service.CustomerExplanationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 客户客服：把温控异常解释成 责任 / 货损 / 预计放行时间，并提供批次运输质量追溯 */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final CustomerExplanationService svc;

    public CustomerController(CustomerExplanationService svc) {
        this.svc = svc;
    }

    @GetMapping("/appointments/{id}/explain")
    public Map<String, Object> explain(@PathVariable Long id) {
        UserAccount u = CurrentUser.get();
        AuthController.requireAny(UserRole.CS, UserRole.DISPATCH, UserRole.SETTLEMENT, UserRole.ADMIN);
        Long customerId = u.getRole() == UserRole.CS ? u.getBindCustomerId() : null;
        return svc.explain(id, customerId);
    }

    @GetMapping("/appointments/{id}/trace")
    public Map<String, Object> trace(@PathVariable Long id) {
        UserAccount u = CurrentUser.get();
        AuthController.requireAny(UserRole.CS, UserRole.DISPATCH, UserRole.SETTLEMENT, UserRole.CARRIER, UserRole.ADMIN);
        Long customerId = u.getRole() == UserRole.CS ? u.getBindCustomerId() : null;
        return svc.trace(id, customerId);
    }
}
