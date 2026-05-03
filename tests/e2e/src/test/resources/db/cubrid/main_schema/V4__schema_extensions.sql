-- ============================================================
-- MAIN_SCHEMA: CUBRID-only extension tables
-- Per docs/seed/cubrid/SEED_SPEC.md §5.5 (collection), §5.6 (enum), §5.7 (json)
-- ============================================================

-- ====== §5.5 e2e_cubrid_collection_types ============================
CREATE TABLE e2e_cubrid_collection_types (
    id                INT NOT NULL,
    set_int_col       SET(INT),
    multiset_str_col  MULTISET(VARCHAR(20)),
    seq_double_col    SEQUENCE(DOUBLE),
    CONSTRAINT pk_e2e_cubrid_collection_types PRIMARY KEY (id)
);

-- ====== §5.6 e2e_cubrid_enum_types ==================================
CREATE TABLE e2e_cubrid_enum_types (
    id          INT NOT NULL,
    status_enum ENUM('NEW', 'HOLD', 'DONE', 'CANCEL'),
    CONSTRAINT pk_e2e_cubrid_enum_types PRIMARY KEY (id)
);

-- ====== §5.7 e2e_cubrid_json_types (CUBRID 11+) =====================
CREATE TABLE e2e_cubrid_json_types (
    id      INT NOT NULL,
    payload JSON,
    CONSTRAINT pk_e2e_cubrid_json_types PRIMARY KEY (id)
);
