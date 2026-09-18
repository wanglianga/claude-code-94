-- ============================================================
-- 冷链车辆预约入场与温控异常协同服务 - 数据库结构
-- ============================================================

CREATE TABLE IF NOT EXISTS app_user (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(64) NOT NULL UNIQUE,
    password     VARCHAR(128) NOT NULL,           -- {noop} 前缀演示口令
    display_name VARCHAR(64) NOT NULL,
    role         VARCHAR(16) NOT NULL CHECK (role IN ('CARRIER','DISPATCH','QC','CS','SETTLE')),
    carrier_id   BIGINT,
    owner_id     BIGINT,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS cargo_owner (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(32) NOT NULL UNIQUE,
    name           VARCHAR(128) NOT NULL,
    priority_level VARCHAR(8) NOT NULL DEFAULT 'P2' CHECK (priority_level IN ('P0','P1','P2')),
    default_value  NUMERIC(12,2) NOT NULL DEFAULT 50.00,  -- 每件货值（用于货损估算）
    contact        VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS carrier (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(32) NOT NULL UNIQUE,
    name         VARCHAR(128) NOT NULL,
    credit_score INT NOT NULL DEFAULT 100,        -- 信用分，影响后续预约优先级
    contact      VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS driver (
    id                  BIGSERIAL PRIMARY KEY,
    carrier_id          BIGINT NOT NULL REFERENCES carrier(id),
    name                VARCHAR(64) NOT NULL,
    license_no          VARCHAR(64) NOT NULL UNIQUE,
    license_expire_date DATE NOT NULL,
    phone               VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS dock (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(16) NOT NULL UNIQUE,
    temp_zone    VARCHAR(16) NOT NULL CHECK (temp_zone IN ('FROZEN','CHILLED','AMBIENT')),
    night_open   BOOLEAN NOT NULL DEFAULT FALSE,  -- 夜间可作业（夜间排队容量受此约束）
    backup_power BOOLEAN NOT NULL DEFAULT FALSE,  -- 自备发电（限电时仍可作业）
    active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS qc_shift (
    id           BIGSERIAL PRIMARY KEY,
    day_of_week  INT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1=周一 ... 7=周日
    start_minute INT NOT NULL CHECK (start_minute BETWEEN 0 AND 1439),
    end_minute   INT NOT NULL CHECK (end_minute BETWEEN 1 AND 1800), -- 可跨夜（>1440 表示次日）
    headcount    INT NOT NULL
);

CREATE TABLE IF NOT EXISTS park_setting (
    key   VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS trip (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(32) NOT NULL UNIQUE,
    carrier_id            BIGINT NOT NULL REFERENCES carrier(id),
    driver_id             BIGINT NOT NULL REFERENCES driver(id),
    vehicle_plate         VARCHAR(16) NOT NULL,
    temp_device_no        VARCHAR(64) NOT NULL,    -- 车厢温度设备号
    e_seal_no             VARCHAR(64) NOT NULL,    -- 电子封签号
    requested_at          TIMESTAMP NOT NULL,      -- 承运商预约时间
    window_start          TIMESTAMP NOT NULL,      -- 系统生成入场窗口
    window_end            TIMESTAMP NOT NULL,
    dock_id               BIGINT REFERENCES dock(id),
    temp_zone             VARCHAR(16) NOT NULL,
    estimated_pieces      INT NOT NULL,
    urgent                BOOLEAN NOT NULL DEFAULT FALSE, -- 客户临时加急
    mixed_loading         BOOLEAN NOT NULL DEFAULT FALSE, -- 多货主混装
    priority_score        INT NOT NULL DEFAULT 0,
    status                VARCHAR(24) NOT NULL DEFAULT 'BOOKED'
        CHECK (status IN ('BOOKED','AT_GATE','QUEUED_AT_GATE','AT_DOCK','IN_QC',
                          'QUARANTINED','AWAIT_DISPOSITION','SETTLING','DONE','CANCELLED')),
    current_stage         VARCHAR(16),             -- GATE/DOCK/QC/QUARANTINE/SETTLE/DONE
    stage_entered_at      TIMESTAMP,
    arrived_at            TIMESTAMP,
    gate_plate_ok         BOOLEAN,
    gate_seal_ok          BOOLEAN,
    queued                BOOLEAN NOT NULL DEFAULT FALSE,
    late                  BOOLEAN NOT NULL DEFAULT FALSE,
    unload_photos         TEXT,                    -- 卸货照片（URL/说明，逗号分隔）
    unloading_started_at  TIMESTAMP,
    unloading_finished_at TIMESTAMP,
    disposition           VARCHAR(16) CHECK (disposition IN ('RELEASED','QUARANTINED','DOWNGRADED','REJECTED')),
    disposition_note      TEXT,
    liability_party       VARCHAR(16) CHECK (liability_party IN ('CARRIER','PARK','OWNER','SHARED')),
    penalty_amount        NUMERIC(12,2) NOT NULL DEFAULT 0,
    compensation_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    liability_note        TEXT,
    settled_at            TIMESTAMP,
    signoff_result        VARCHAR(16) CHECK (signoff_result IN ('SIGNED','PARTIAL','REJECTED')),
    signed_pieces         INT,
    signed_by             VARCHAR(64),
    signoff_note          TEXT,
    signed_at             TIMESTAMP,
    created_by            VARCHAR(64),
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_trip_window ON trip(window_start, temp_zone);
CREATE INDEX IF NOT EXISTS idx_trip_stage ON trip(current_stage, status);

CREATE TABLE IF NOT EXISTS trip_cargo (
    id                 BIGSERIAL PRIMARY KEY,
    trip_id            BIGINT NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    owner_id           BIGINT NOT NULL REFERENCES cargo_owner(id),
    cargo_type         VARCHAR(64) NOT NULL,
    temp_zone          VARCHAR(16) NOT NULL,
    max_temp_celsius   NUMERIC(5,2) NOT NULL,      -- 允许的最高厢温
    pieces             INT NOT NULL,
    value_per_piece    NUMERIC(12,2) NOT NULL DEFAULT 50.00,
    target_warehouse   VARCHAR(64),                -- 目标仓（货主临时改仓会更新）
    door_open_temp     NUMERIC(5,2),               -- 开门前温度
    damaged_pieces     INT NOT NULL DEFAULT 0,     -- 破损件数
    sample_total       INT,                        -- 质检抽样件数
    sample_thawed      INT NOT NULL DEFAULT 0,     -- 化冻件数
    qc_result          VARCHAR(16) CHECK (qc_result IN ('PASS','THAWED','DAMAGE_ONLY')),
    disposition        VARCHAR(16) CHECK (disposition IN ('RELEASED','QUARANTINED','DOWNGRADED','REJECTED')),
    disposition_note   TEXT
);
CREATE INDEX IF NOT EXISTS idx_cargo_trip ON trip_cargo(trip_id);

-- 温控设备原始数据（完整保留，供客户追溯）
CREATE TABLE IF NOT EXISTS temp_reading (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     BIGINT NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    device_no   VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    celsius     NUMERIC(5,2) NOT NULL,
    gap_after   BOOLEAN NOT NULL DEFAULT FALSE     -- 该点之后存在曲线断点
);
CREATE INDEX IF NOT EXISTS idx_reading_trip_time ON temp_reading(trip_id, recorded_at);

CREATE TABLE IF NOT EXISTS incident (
    id             BIGSERIAL PRIMARY KEY,
    trip_id        BIGINT NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    type           VARCHAR(24) NOT NULL CHECK (type IN (
                       'LATE_ARRIVAL','TEMP_GAP','SEAL_ABNORMAL','WAREHOUSE_CHANGE',
                       'DOCK_CONGESTION','THAW_FOUND','DOCS_EXPIRED','POWER_LIMIT',
                       'URGENT_INSERT','TEMP_EXCURSION')),
    severity       VARCHAR(8) NOT NULL DEFAULT 'MED' CHECK (severity IN ('LOW','MED','HIGH')),
    description    TEXT NOT NULL,
    status         VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED')),
    responsibility VARCHAR(16) CHECK (responsibility IN ('CARRIER','PARK','OWNER','PENDING')),
    raised_by_role VARCHAR(16),
    resolution     TEXT,
    resolved_by    VARCHAR(64),
    created_at     TIMESTAMP NOT NULL,
    resolved_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_incident_trip ON incident(trip_id, status);

-- 同一车次的协同时间线：承运商/调度/质检/客服/结算共享
CREATE TABLE IF NOT EXISTS trip_event (
    id         BIGSERIAL PRIMARY KEY,
    trip_id    BIGINT NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    actor_role VARCHAR(16),
    actor_name VARCHAR(64),
    detail     TEXT,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_event_trip ON trip_event(trip_id, created_at);
