-- ============================================================
-- MAIN_SCHEMA: type test tables (core only — CUBRID extensions live in V4)
-- Per docs/seed/cubrid/SEED_SPEC.md §5.1 ~ §5.4
-- ============================================================

-- ====== §5.1 e2e_text_types =========================================
CREATE TABLE e2e_text_types (
    id           INT          NOT NULL,
    char_col     CHAR(3),
    varchar_col  VARCHAR(10),
    nchar_col    NCHAR(3),
    nvarchar_col NCHAR VARYING(40),
    string_col   STRING,
    clob_col     CLOB,
    CONSTRAINT pk_e2e_text_types PRIMARY KEY (id)
);

-- ====== §5.2 e2e_numeric_types ======================================
CREATE TABLE e2e_numeric_types (
    id             INT NOT NULL,
    smallint_col   SMALLINT,
    int_col        INT,
    bigint_col     BIGINT,
    numeric_col    NUMERIC(20, 6),
    numeric_p0_col NUMERIC(38, 0),
    float_col      FLOAT,
    double_col     DOUBLE,
    monetary_col   MONETARY,
    CONSTRAINT pk_e2e_numeric_types PRIMARY KEY (id)
);

-- ====== §5.3 e2e_temporal_types =====================================
CREATE TABLE e2e_temporal_types (
    id               INT NOT NULL,
    date_col         DATE,
    time_col         TIME,
    datetime_col     DATETIME,
    timestamp_col    TIMESTAMP,
    datetimetz_col   DATETIMETZ,
    datetimeltz_col  DATETIMELTZ,
    timestamptz_col  TIMESTAMPTZ,
    timestampltz_col TIMESTAMPLTZ,
    CONSTRAINT pk_e2e_temporal_types PRIMARY KEY (id)
);

-- ====== §5.4 e2e_binary_types =======================================
CREATE TABLE e2e_binary_types (
    id         INT NOT NULL,
    bit_col    BIT(64),
    varbit_col BIT VARYING(1024),
    blob_col   BLOB,
    CONSTRAINT pk_e2e_binary_types PRIMARY KEY (id)
);
