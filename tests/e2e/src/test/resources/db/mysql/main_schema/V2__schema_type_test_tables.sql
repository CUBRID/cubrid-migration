-- ============================================================
-- MAIN_SCHEMA (MySQL): type test tables.
--
-- Each table holds the canonical battery rows (R_NULL / R_EMPTY / R_MIN /
-- R_MAX / R_UNICODE / R_BOUNDARY) inserted from V99__data.sql, exercising the
-- MySQL -> CUBRID type mapping. MySQL-specific ENUM/SET/JSON are intentionally
-- omitted: CMT's MySQL exporter builds an invalid extraction query for them.
-- ============================================================

-- ====== text ======================================================
CREATE TABLE e2e_text_types (
    id             INT NOT NULL,
    char_col       CHAR(10),
    varchar_col    VARCHAR(100),
    tinytext_col   TINYTEXT,
    text_col       TEXT,
    mediumtext_col MEDIUMTEXT,
    longtext_col   LONGTEXT,
    PRIMARY KEY (id)
);

-- ====== numeric ===================================================
CREATE TABLE e2e_numeric_types (
    id            INT NOT NULL,
    tinyint_col   TINYINT,
    smallint_col  SMALLINT,
    mediumint_col MEDIUMINT,
    int_col       INT,
    bigint_col    BIGINT,
    decimal_col   DECIMAL(20, 4),
    float_col     FLOAT,
    double_col    DOUBLE,
    bit_col       BIT(8),
    PRIMARY KEY (id)
);

-- ====== temporal ==================================================
-- timestamp_col stays within the MySQL/CUBRID TIMESTAMP window (1970..2038);
-- time_col stays within a 24h clock because CUBRID TIME is time-of-day only.
CREATE TABLE e2e_temporal_types (
    id            INT NOT NULL,
    date_col      DATE,
    datetime_col  DATETIME(6),
    timestamp_col TIMESTAMP(6) NULL,
    time_col      TIME(6),
    year_col      YEAR,
    PRIMARY KEY (id)
);

-- ====== binary ====================================================
CREATE TABLE e2e_binary_types (
    id             INT NOT NULL,
    binary_col     BINARY(16),
    varbinary_col  VARBINARY(255),
    tinyblob_col   TINYBLOB,
    blob_col       BLOB,
    mediumblob_col MEDIUMBLOB,
    longblob_col   LONGBLOB,
    PRIMARY KEY (id)
);
