package com.coldchain.park.config;

import com.coldchain.park.domain.Appointment;
import com.coldchain.park.domain.Carrier;
import com.coldchain.park.domain.Customer;
import com.coldchain.park.domain.Dock;
import com.coldchain.park.domain.Driver;
import com.coldchain.park.domain.ParkState;
import com.coldchain.park.domain.Priority;
import com.coldchain.park.domain.TempZone;
import com.coldchain.park.domain.UserAccount;
import com.coldchain.park.domain.UserRole;
import com.coldchain.park.repo.AppointmentRepository;
import com.coldchain.park.repo.CarrierRepository;
import com.coldchain.park.repo.CustomerRepository;
import com.coldchain.park.repo.DockRepository;
import com.coldchain.park.repo.DriverRepository;
import com.coldchain.park.repo.ParkStateRepository;
import com.coldchain.park.repo.UserRepository;
import com.coldchain.park.service.AppointmentService;
import com.coldchain.park.web.dto.Dtos;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 演示数据：账号、承运商、货主、司机（含证件过期）、月台、园区状态，并预约一辆混装车 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final CarrierRepository carriers;
    private final CustomerRepository customers;
    private final DriverRepository drivers;
    private final DockRepository docks;
    private final ParkStateRepository parkState;
    private final AppointmentRepository appts;
    private final AppointmentService apptService;

    public DataSeeder(UserRepository users, CarrierRepository carriers, CustomerRepository customers,
                      DriverRepository drivers, DockRepository docks, ParkStateRepository parkState,
                      AppointmentRepository appts, AppointmentService apptService) {
        this.users = users;
        this.carriers = carriers;
        this.customers = customers;
        this.drivers = drivers;
        this.docks = docks;
        this.parkState = parkState;
        this.appts = appts;
        this.apptService = apptService;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) return;

        Carrier c1 = carrier("CAR01", "顺达冷链物流", "周经理", "13800000001", 100, 0);
        Carrier c2 = carrier("CAR02", "极光冷链运输", "吴队长", "13800000002", 55, 1600);

        Customer cu1 = customer("CUS01", "海得鲜食品", "林主管", "13900000001",
                Priority.VIP, 3200, 80);
        Customer cu2 = customer("CUS02", "晨光乳业", "赵客服", "13900000002",
                Priority.STANDARD, 2600, 30);
        Customer cu3 = customer("CUS03", "金麦烘焙", "钱店长", "13900000003",
                Priority.ECONOMY, 2200, 20);

        driver(c1, "张伟", "370102198803051234", LocalDate.of(2027, 5, 1), LocalDate.of(2027, 8, 1));
        driver(c1, "陈刚", "370102198507212233", LocalDate.of(2028, 3, 1), LocalDate.of(2027, 12, 1));
        driver(c1, "李强", "370102197911126677", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 6, 1)); // 驾驶证已过期
        driver(c2, "王磊", "370102199001018899", LocalDate.of(2028, 1, 1), LocalDate.of(2028, 1, 1));

        docks.save(new Dock("D1-冷冻", TempZone.FROZEN));
        docks.save(new Dock("D2-冷冻", TempZone.FROZEN));
        docks.save(new Dock("D3-冷藏", TempZone.CHILLED));
        docks.save(new Dock("D4-冷藏", TempZone.CHILLED));
        docks.save(new Dock("D5-恒温", TempZone.CONST));
        docks.save(new Dock("D6-通用", null));

        ParkState ps = new ParkState();
        ps.setPowerShedding(false);
        ps.setPowerNote("电力正常");
        parkState.save(ps);

        user("admin", "admin123", "系统管理员", UserRole.ADMIN, null, null);
        user("carrier1", "carrier123", "顺达-周经理", UserRole.CARRIER, c1.getId(), null);
        user("carrier2", "carrier123", "极光-吴队长", UserRole.CARRIER, c2.getId(), null);
        user("dispatch", "disp123", "园区调度-孙敏", UserRole.DISPATCH, null, null);
        user("qc", "qc123", "质检员-郑洁", UserRole.QC, null, null);
        user("cs_haixian", "cs123", "海得鲜客服-林主管", UserRole.CS, null, cu1.getId());
        user("cs_niunai", "cs123", "晨光客服-赵客服", UserRole.CS, null, cu2.getId());
        user("settle", "settle123", "结算员-冯丽", UserRole.SETTLEMENT, null, null);

        // 预置一辆多货主混装的预约车（冷冻 + 冷藏混装），2 小时后到园
        if (appts.count() == 0) {
            LocalDateTime t = LocalDateTime.now().plusHours(2).withMinute(0).withSecond(0).withNano(0);
            Dtos.CreateApptReq req = new Dtos.CreateApptReq(
                    c1.getId(),
                    drivers.findByCarrierId(c1.getId()).get(0).getId(),
                    "鲁B12345", "T-DEV-8801", "SEAL-A998877",
                    "冻虾仁/冷鲜牛奶（混装）", 300, t, false,
                    List.of(
                            new Dtos.CargoLineReq(cu1.getId(), "冻虾仁 4斤装", TempZone.FROZEN, 200, "1号冷库-A区"),
                            new Dtos.CargoLineReq(cu2.getId(), "巴氏鲜牛奶", TempZone.CHILLED, 100, "2号冷库-B区")
                    ));
            apptService.create(req);
        }
    }

    private Carrier carrier(String code, String name, String contact, String phone, int score, double penalty) {
        Carrier c = new Carrier();
        c.setCode(code);
        c.setName(name);
        c.setContact(contact);
        c.setPhone(phone);
        c.setServiceScore(score);
        c.setTotalPenalty(penalty);
        return carriers.save(c);
    }

    private Customer customer(String code, String name, String contact, String phone,
                              Priority p, double freight, double claim) {
        Customer c = new Customer();
        c.setCode(code);
        c.setName(name);
        c.setContact(contact);
        c.setPhone(phone);
        c.setPriority(p);
        c.setContractFreight(freight);
        c.setClaimPricePerPiece(claim);
        return customers.save(c);
    }

    private void driver(Carrier c, String name, String idNo, LocalDate license, LocalDate qual) {
        Driver d = new Driver();
        d.setCarrier(c);
        d.setName(name);
        d.setPhone("137" + String.format("%08d", Math.abs(name.hashCode() % 100000000)));
        d.setIdNo(idNo);
        d.setLicenseExpiry(license);
        d.setQualificationExpiry(qual);
        drivers.save(d);
    }

    private void user(String username, String pwd, String display, UserRole role, Long carrierId, Long customerId) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPassword(pwd);
        u.setDisplayName(display);
        u.setRole(role);
        u.setBindCarrierId(carrierId);
        u.setBindCustomerId(customerId);
        u.setEnabled(true);
        users.save(u);
    }
}
