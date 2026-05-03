-- ============================================================
-- MAIN_SCHEMA: type test tables
-- Per oracle/SEED_SPEC.md §5.1 ~ §5.5
--
-- Each table holds the canonical R_NULL/R_EMPTY/R_MIN/R_MAX/(R_UNICODE)/
-- R_BOUNDARY (and §5.2 also R_REPRESENTATIVE) battery rows. The actual row
-- literals are inserted from main_schema/V99__data.sql.
--
-- e2e_oracle_locator_types is an Oracle-only extension table (§5.5),
-- explicitly separated from the cross-DB Core Dataset by COMMON_SEED_CONTRACT
-- §3 and SEED_DATA_GUIDE §5.3 (DB-specific pseudo types stay out of core).
-- ============================================================

-- ====== §5.1 e2e_text_types ======================================
CREATE TABLE e2e_text_types (
    id               NUMBER(10) NOT NULL,
    char_byte_col    CHAR(3 BYTE),
    char_char_col    CHAR(3 CHAR),
    varchar_byte_col VARCHAR2(10 BYTE),
    varchar_char_col VARCHAR2(10 CHAR),
    nchar_col        NCHAR(10),
    nvarchar_col     NVARCHAR2(40),
    clob_col         CLOB,
    nclob_col        NCLOB,
    CONSTRAINT pk_e2e_text_types PRIMARY KEY (id)
);

-- ====== §5.2 e2e_numeric_types ===================================
CREATE TABLE e2e_numeric_types (
    id                NUMBER(10) NOT NULL,
    integer_col       INTEGER,
    decimal_col       DECIMAL(20,4),
    number_p0_col     NUMBER(38,0),
    number_ps_col     NUMBER(20,6),
    number_round_col  NUMBER(8,-2),
    number_any_col    NUMBER,
    float_col         FLOAT(30),
    real_col          REAL,
    binary_float_col  BINARY_FLOAT,
    binary_double_col BINARY_DOUBLE,
    CONSTRAINT pk_e2e_numeric_types PRIMARY KEY (id)
);

-- ====== §5.3 e2e_temporal_types ==================================
CREATE TABLE e2e_temporal_types (
    id              NUMBER(10) NOT NULL,
    date_col        DATE,
    ts6_col         TIMESTAMP(6),
    ts9_col         TIMESTAMP(9),
    tsltz6_col      TIMESTAMP(6) WITH LOCAL TIME ZONE,
    tstz9_col       TIMESTAMP(9) WITH TIME ZONE,
    interval_ds_col INTERVAL DAY(2) TO SECOND(6),
    interval_ym_col INTERVAL YEAR(4) TO MONTH,
    CONSTRAINT pk_e2e_temporal_types PRIMARY KEY (id)
);

-- ====== §5.4 e2e_binary_types ====================================
CREATE TABLE e2e_binary_types (
    id       NUMBER(10) NOT NULL,
    raw_col  RAW(16),
    blob_col BLOB,
    CONSTRAINT pk_e2e_binary_types PRIMARY KEY (id)
);

-- ====== §5.5 e2e_oracle_locator_types (Oracle extension) =========
CREATE TABLE e2e_oracle_locator_types (
    id         NUMBER(10) NOT NULL,
    rowid_col  ROWID,
    urowid_col UROWID,
    CONSTRAINT pk_e2e_oracle_locator_types PRIMARY KEY (id)
);
