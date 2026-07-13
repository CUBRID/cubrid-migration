-- ============================================================
-- e2e_db (Informix): type test tables (ASCII battery).
--
-- Exercises the Informix -> CUBRID type mapping. Simple-LOB types (TEXT/BYTE)
-- and BLOB are omitted because they cannot be inserted through plain-SQL
-- literals via the JDBC statement runner (the driver demands a blob handle);
-- LVARCHAR covers long character data. INTERVAL is omitted as well.
-- ============================================================

CREATE TABLE e2e_text_types (
    id           INTEGER NOT NULL,
    char_col     CHAR(10),
    varchar_col  VARCHAR(100),
    lvarchar_col LVARCHAR(2000),
    PRIMARY KEY (id) CONSTRAINT pk_e2e_text_types
);

CREATE TABLE e2e_numeric_types (
    id             INTEGER NOT NULL,
    smallint_col   SMALLINT,
    int_col        INTEGER,
    bigint_col     BIGINT,
    decimal_col    DECIMAL(20, 4),
    money_col      MONEY(16, 2),
    smallfloat_col SMALLFLOAT,
    float_col      FLOAT,
    boolean_col    BOOLEAN,
    PRIMARY KEY (id) CONSTRAINT pk_e2e_numeric_types
);

CREATE TABLE e2e_temporal_types (
    id          INTEGER NOT NULL,
    date_col    DATE,
    dt_sec_col  DATETIME YEAR TO SECOND,
    dt_frac_col DATETIME YEAR TO FRACTION(5),
    PRIMARY KEY (id) CONSTRAINT pk_e2e_temporal_types
);
