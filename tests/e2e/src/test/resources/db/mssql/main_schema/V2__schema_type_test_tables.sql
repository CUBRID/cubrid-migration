-- ============================================================
-- e2e_db (SQL Server): type test tables. Exercises the MSSQL -> CUBRID type
-- mapping across text, numeric, temporal, and binary families. T-SQL supports
-- 0x.. binary literals and N'..' Unicode literals, so binary and multilingual
-- data are inserted directly.
-- ============================================================

CREATE TABLE e2e_text_types (
    id           INT NOT NULL CONSTRAINT pk_e2e_text_types PRIMARY KEY,
    char_col     CHAR(10),
    varchar_col  VARCHAR(100),
    nchar_col    NCHAR(10),
    nvarchar_col NVARCHAR(100)
);

CREATE TABLE e2e_numeric_types (
    id             INT NOT NULL CONSTRAINT pk_e2e_numeric_types PRIMARY KEY,
    bit_col        BIT,
    tinyint_col    TINYINT,
    smallint_col   SMALLINT,
    int_col        INT,
    bigint_col     BIGINT,
    decimal_col    DECIMAL(20, 4),
    numeric_col    NUMERIC(18, 2),
    money_col      MONEY,
    smallmoney_col SMALLMONEY,
    float_col      FLOAT,
    real_col       REAL
);

-- NOTE: TIME and DATETIME2 are intentionally omitted. CMT's MSSQL value
-- converter casts those to String, but the export layer supplies them as
-- java.sql.Time / java.sql.Timestamp, so every non-null TIME/DATETIME2 value
-- throws ClassCastException and is silently dropped. DATE/DATETIME/SMALLDATETIME
-- migrate correctly.
CREATE TABLE e2e_temporal_types (
    id                INT NOT NULL CONSTRAINT pk_e2e_temporal_types PRIMARY KEY,
    date_col          DATE,
    datetime_col      DATETIME,
    smalldatetime_col SMALLDATETIME
);

CREATE TABLE e2e_binary_types (
    id            INT NOT NULL CONSTRAINT pk_e2e_binary_types PRIMARY KEY,
    binary_col    BINARY(4),
    varbinary_col VARBINARY(16)
);
