package com.coldchain.park.web;

import com.coldchain.park.domain.Priority;
import com.coldchain.park.domain.TempZone;
import com.coldchain.park.repo.CarrierRepository;
import com.coldchain.park.repo.CustomerRepository;
import com.coldchain.park.repo.DockRepository;
import com.coldchain.park.repo.DriverRepository;
import com.coldchain.park.repo.ParkStateRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 基础档案与枚举，供下拉选择与演示 */
@RestController
@RequestMapping("/api/meta")
@Transactional(readOnly = true)
public class MetaController {

    private final CarrierRepository carriers;
    private final CustomerRepository customers;
    private final DriverRepository drivers;
    private final DockRepository docks;
    private final ParkStateRepository parkState;

    public MetaController(CarrierRepository carriers, CustomerRepository customers,
                          DriverRepository drivers, DockRepository docks,
                          ParkStateRepository parkState) {
        this.carriers = carriers;
        this.customers = customers;
        this.drivers = drivers;
        this.docks = docks;
        this.parkState = parkState;
    }

    @GetMapping("/carriers")
    public List<Map<String, Object>> carriers() {
        return carriers.findAll().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("code", c.getCode());
            m.put("name", c.getName());
            m.put("serviceScore", c.getServiceScore());
            m.put("totalPenalty", c.getTotalPenalty());
            return m;
        }).toList();
    }

    @GetMapping("/customers")
    public List<Map<String, Object>> customers() {
        return customers.findAll().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("code", c.getCode());
            m.put("name", c.getName());
            m.put("priority", c.getPriority().name());
            m.put("priorityLabel", c.getPriority().label);
            m.put("contractFreight", c.getContractFreight());
            m.put("claimPricePerPiece", c.getClaimPricePerPiece());
            return m;
        }).toList();
    }

    @GetMapping("/drivers")
    public List<Map<String, Object>> drivers() {
        return drivers.findAll().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getName());
            m.put("carrierId", d.getCarrier().getId());
            m.put("carrierName", d.getCarrier().getName());
            m.put("licenseExpiry", d.getLicenseExpiry());
            m.put("qualificationExpiry", d.getQualificationExpiry());
            return m;
        }).toList();
    }

    @GetMapping("/docks")
    public List<Map<String, Object>> docks() {
        return docks.findByEnabledTrue().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("code", d.getCode());
            m.put("preferredZone", d.getPreferredZone() == null ? null : d.getPreferredZone().name());
            m.put("zoneLabel", d.getPreferredZone() == null ? "通用温区" : d.getPreferredZone().label);
            return m;
        }).toList();
    }

    @GetMapping("/enums")
    public Map<String, Object> enums() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("zones", enumList(TempZone.values()));
        m.put("priorities", enumList(Priority.values()));
        return m;
    }

    private List<Map<String, Object>> enumList(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(e -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", e.name());
            x.put("label", e instanceof TempZone z ? z.label
                    : e instanceof Priority p ? p.label : e.name());
            return x;
        }).toList();
    }
}
