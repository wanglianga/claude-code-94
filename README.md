# 物流园冷链车辆预约入场与温控异常协同服务

基于 **Java 17 + Spring Boot 3 + PostgreSQL** 的 0-1 工程，覆盖冷链车辆「预约 → 窗口生成 → 闸口核验 → 月台接车 → 温度/卸货/质检 → 异常多方协同 → 处置定责结算 → 客户签收追溯」全流程。

---

## 原始需求

> 开发物流园冷链车辆预约入场与温控异常服务，可使用 Java、Spring Boot 和 PostgreSQL。承运商提交车牌、司机、货主、货品类型、温区要求、预约时间、预计件数、车厢温度设备和电子封签后，服务根据月台容量、质检人员班次和客户优先级生成入场窗口。车辆到园时，闸口核验预约、车牌和封签，月台接车后上传开门前温度、卸货照片、破损件数和质检抽样结果。若车辆晚到、温度曲线断点、封签异常、货主临时改仓、月台拥堵或质检发现化冻，服务需要让承运商、园区调度、质检、客户客服和结算人员在同一车次中处理。货物放行、隔离、降级入库或拒收后，责任判断会影响承运商扣罚、客户赔付和后续预约优先级。服务还要保留温控设备原始数据、卸货时长和签收结果，便于客户追溯某批冷链商品的运输质量。服务还要兼顾多货主混装、夜间排队、司机证件过期、客户临时加急和园区限电。调度人员需要看到每辆车卡在闸口、月台、质检、隔离还是结算环节，客户客服则需要把温控异常解释成可理解的责任、货损和预计放行时间。

---

## 技术栈与运行形态

- Spring Boot 3.3（Web / JDBC / Security / Validation），JdbcTemplate 直写 SQL，无额外重型依赖。
- PostgreSQL 16（仅 compose 内网，**不发布到宿主**）。
- 多阶段 Dockerfile：Maven 构建 → JRE 运行；**非 root 用户** + **HEALTHCHECK**。
- 鉴权：HTTP Basic + 五类角色 `CARRIER / DISPATCH / QC / CS / SETTLE`，无状态。
- 库表由 `schema.sql` 自动建表、`data.sql` 自动播种（幂等，可重复启动）。

## 快速启动（宿主 docker compose 一键部署）

```bash
cp .env.example .env        # CC_PUBLISH_PORT 已由验证环境注入，也可自行修改
docker compose up -d --build
# 健康检查（start_period 约 90 秒，首次需 Maven 拉依赖+构建镜像）
curl -s http://localhost:${CC_PUBLISH_PORT}/api/health
```

只发布应用端口：`ports: ["${CC_PUBLISH_PORT}:8080"]`；数据库仅在 compose 网络内通过服务名 `db` 访问。

验证实际映射端口（并行任务端口不冲突）：

```bash
docker compose port app 8080
# 浏览器/接口访问： http://host.docker.internal:<映射端口>/api/health
```

停止并释放资源：

```bash
docker compose down
```

> 演示“晚到 / 夜间排队”等时间场景：在 `.env` 设置 `PARK_CLOCK=2026-09-18T13:00:00`（园区基准时钟，按真实流逝推进）；留空则使用容器当前时间。

## 测试账号（逐角色）

| 用户名 | 密码 | 角色 | 权限/可执行操作 |
|---|---|---|---|
| `carrier1` | `carrier123` | 承运商 CARRIER（顺丰冷链） | 提交/查看本公司预约、上传温控设备原始数据、查看车次时间线与追溯 |
| `carrier2` | `carrier123` | 承运商 CARRIER（京东冷链） | 同上 |
| `dispatch` | `dispatch123` | 园区调度 DISPATCH | 闸口核验放行、改约、货主改仓、限电开关、异常闭环定责、排队放行、调度看板 |
| `qc` | `qc123` | 质检员 QC | 月台卸货登记（开门前温度/照片/破损）、质检抽样、放行/隔离/降级/拒收处置决定 |
| `cs` | `cs123` | 客户客服 CS | 查看客户可理解的异常解释（责任/货损/预计放行时间）、客户签收、运输质量追溯 |
| `settle` | `settle123` | 结算员 SETTLE | 责任金额测算预览、确认扣罚/赔付、落地承运商信用分调整 |

## 演示主数据

- 货主与客户优先级：`盒马鲜生 P0`、`蒙牛乳业 P1`、`永辉超市 P2`。
- 承运商：顺丰冷链（信用分 100）、京东冷链（85）。
- 司机：张志强（证件有效）、**李大山（证件 2026-01-15 已过期，用于演示证件拦截）**、王海涛。
- 月台：`D01` 冷冻/夜间/自备发电、`D02` 冷藏/无备电、`D03` 常温/夜间、`D04` 冷藏/夜间/自备发电、`D05` 冷冻/无备电。
- 质检班次：工作日 08:00-18:00（4 人）、周末 09:00-18:00（3 人）、每日 20:00-24:00 夜班。
- 园区参数：夜间 22:00-06:00、晚到容忍 30 分钟、窗口 30 分钟、温度断点阈值 45 分钟、限电开关默认关。

## 车次状态机（环节）

```
BOOKED ──闸口核验──> AT_GATE ──月台接车──> AT_DOCK ──卸货完成──> IN_QC
  │                     │                     │
  │(封签/证件/晚到/     │              质检无化冻│有化冻
  │ 拥堵/限电)          ▼                     ▼
  └──────────────> QUEUED_AT_GATE      AWAIT_DISPOSITION / QUARANTINED
                     (调度处置后放行)            │ 处置决定(放行/隔离/降级/拒收)
                                                 ▼
                                            SETTLING ──结算确认──> DONE ──客服签收
```

调度看板 `/api/board/overview` 直接给出每辆车的 `currentStage`（闸口/月台/质检/隔离/结算）与卡点原因。

## 需求点 → 实现映射

| 需求 | 实现 |
|---|---|
| 月台容量约束生成窗口 | `SchedulingService` 按 30 分钟窗口检查同月台时间重叠 |
| 质检人员班次 | 窗口必须被 `qc_shift` 覆盖，同窗在班车次 ≤ 当班人数 |
| 客户优先级 | P0/P1/P2 + 承运商信用分 + 加急 50 分合成 `priority_score`，加急插队、排队按优先级 |
| 多货主混装 | 一次预约多条 `cargoLines`（不同货主/温区），主温区取最严格，逐货主质检/处置/结算 |
| 夜间排队 | 22:00-06:00 仅排 `night_open` 月台，登记排队事件 |
| 司机证件过期 | 预约即登记 `DOCS_EXPIRED` 高优异常，闸口拦截排队 |
| 客户临时加急 | `urgent=true` 提分插队，登记 `URGENT_INSERT` |
| 园区限电 | 限电开关后仅排 `backup_power` 月台，已约车辆批量生成 `POWER_LIMIT` 异常需改约 |
| 闸口核验预约/车牌/封签 | `/api/gate/checkin`，车牌不符直接拦截，封签异常排队待调度核验 |
| 晚到 | 超窗口结束+30 分钟判定，登记 `LATE_ARRIVAL`，调度改约闭环 |
| 月台拥堵 | 月台在园占用时新到车进入闸口排队，入园自动闭环拥堵异常 |
| 温度曲线断点 | 上传原始温度点，相邻间隔 >45 分钟标记 `gap_after` 并生成 `TEMP_GAP` |
| 开门前温度/卸货照片/破损件数 | `/api/dock/trips/{code}/unload`，温度落到每条货主明细 |
| 质检抽样化冻 | `/api/qc/trips/{code}/sample`，化冻自动转 `QUARANTINED` 隔离 |
| 同一车次多方协同 | `incident` + `trip_event` 时间线，五角色共享，`/api/incidents/trips/{code}` |
| 货主临时改仓 | `/api/dispatch/trips/{code}/warehouse-change` 登记 `WAREHOUSE_CHANGE` |
| 放行/隔离/降级/拒收 | `/api/disposition/trips/{code}/decide`（可按货主明细分别处置） |
| 责任→扣罚/赔付/优先级 | 结算按异常责任归集（承运/园区/货主/多方），扣罚+客户赔付+承运商信用分下调（直接影响后续窗口优先级） |
| 温控原始数据/卸货时长/签收追溯 | `/api/trace/trips/{code}` 全量原始温度点、断点、卸货时长、质检、处置、结算、签收 |
| 调度环节看板 | `/api/board/overview`、`/api/board/incidents` |
| 客服可理解解释 | `/api/cs/trips/{code}/explanation` 输出客户语言的责任/货损/预计放行时间 |

## 主要接口（均需 Basic 认证，除 `/api/health`）

| 方法 & 路径 | 角色 | 说明 |
|---|---|---|
| POST `/api/appointments` | CARRIER | 提交预约，返回含系统生成窗口的车次 |
| GET `/api/appointments/{code}` | 全部 | 车次详情（货物/异常/时间线/温度原始数据） |
| GET `/api/my/trips` | 全部 | 承运商看本公司，其它角色看全部 |
| POST `/api/gate/checkin` | DISPATCH | 闸口核验（车牌/封签，晚到/拥堵/限电判定） |
| GET `/api/gate/queue` | DISPATCH | 闸口排队队列（按加急+优先级） |
| POST `/api/dock/trips/{code}/receive` | DISPATCH | 月台接车，卸货计时开始 |
| POST `/api/dock/trips/{code}/temperature` | CARRIER | 上传温控设备原始数据（断点/超限自动识别） |
| POST `/api/dock/trips/{code}/unload` | QC | 开门前温度、卸货照片、破损件数 |
| POST `/api/qc/trips/{code}/sample` | QC | 质检抽样结果（化冻自动隔离） |
| POST `/api/dispatch/trips/{code}/incidents/{id}/resolve` | DISPATCH | 异常闭环并定责 |
| POST `/api/dispatch/trips/{code}/warehouse-change` | DISPATCH | 货主临时改仓 |
| POST `/api/dispatch/trips/{code}/rebook` | DISPATCH | 重新生成入场窗口（晚到/限电/拥堵处置） |
| POST `/api/dispatch/trips/{code}/admit-queue` | DISPATCH | 排队车辆处置后放行入园 |
| POST `/api/dispatch/power-limit` | DISPATCH | 园区限电开关 |
| GET `/api/dispatch/queue` | DISPATCH | 排队队列 |
| GET `/api/board/overview` | DISPATCH/QC/SETTLE | 环节看板（每辆车卡在哪） |
| GET `/api/board/incidents` | DISPATCH/QC/SETTLE | 全量未闭环异常台 |
| POST `/api/disposition/trips/{code}/decide` | QC/DISPATCH | 放行/隔离/降级/拒收 |
| GET `/api/settle/trips/{code}/preview` | SETTLE | 责任与扣罚/赔付测算 |
| POST `/api/settle/trips/{code}/confirm` | SETTLE | 结算确认，调整信用分，车次 DONE |
| GET `/api/cs/trips/{code}/explanation` | CS | 客户语言的责任/货损/预计放行时间 |
| POST `/api/signoff/trips/{code}` | CS | 客户签收（SIGNED/PARTIAL/REJECTED） |
| GET `/api/trace/trips/{code}` | CS/CARRIER/DISPATCH/SETTLE | 运输质量全量追溯档案 |
| GET `/api/trace/owners/{ownerId}/trips` | 同上 | 按货主查批次 |

## 一键端到端验证脚本

```bash
# 需宿主机有 curl；CC_PUBLISH_PORT 为应用宿主端口
export CC_PUBLISH_PORT=$(docker compose port app 8080 | cut -d: -f2)
bash scripts/smoke.sh
```

脚本依次跑通：① 多货主混装正常车全流程（预约→闸口→月台→温度→质检→放行→结算→签收→追溯）；② 温度断点+超限+化冻车（隔离→降级→承运商全责扣罚赔付→信用分下调→客服解释）；③ 封签异常闸口排队→调度核验闭环→放行；④ 证件过期车闸口拦截；⑤ 限电开关后仅自备发电月台可排窗；⑥ 调度看板与异常台。

## 目录结构

```
├── Dockerfile                 # 多阶段构建 + 非 root + HEALTHCHECK
├── docker-compose.yml         # app + postgres（仅发布 app 端口）
├── .env.example
├── pom.xml
├── scripts/smoke.sh           # 端到端业务流冒烟
└── src/main/
    ├── resources/
    │   ├── application.yml
    │   ├── schema.sql         # 表结构
    │   └── data.sql           # 角色账号/主数据
    └── java/com/logpark/coldchain/
        ├── config/            # Spring Security 五角色
        ├── support/           # 时钟、异常、全局错误
        ├── repo/Db.java
        ├── service/           # 排程/预约/闸口/月台质检/调度/处置结算/客服/看板/追溯
        └── web/               # REST 控制器
```
