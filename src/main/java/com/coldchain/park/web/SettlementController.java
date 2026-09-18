package com.coldchain.park.web;

import com.coldchain.park.domain.Settlement;
import com.coldchain.park.domain.UserRole;
import com.coldchain.park.service.AppointmentService;
import com.coldchain.park.service.SettlementService;
import com.coldchain.park.web.dto.Dtos;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** 结算：放行/降级/拒收之后登记签收结果，生成扣罚/赔付结算单 */
@RestController
@RequestMapping("/api/appointments/{id}")
public class SettlementController {

    private final SettlementService settlements;
    private final AppointmentService appts;

    public SettlementController(SettlementService settlements, AppointmentService appts) {
        this.settlements = settlements;
        this.appts = appts;
    }

    @PostMapping("/settle")
    public Map<String, Object> settle(@PathVariable Long id, @RequestBody Dtos.SignSettleReq req) {
        AuthController.requireAny(UserRole.SETTLEMENT);
        Settlement s = settlements.settle(id, req.signResult(), req.signedPieces(),
                req.note(), AuthController.actor());
        Map<String, Object> m = appts.detailMap(id);
        m.put("settlementCode", s.getCode());
        return m;
    }
}
