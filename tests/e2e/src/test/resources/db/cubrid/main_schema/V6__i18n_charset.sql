-- e2e_i18n: UTF-8 character-set battery, one row per script/category.
-- sample is STRING (unbounded) so only charset correctness is exercised,
-- not VARCHAR byte-length truncation.

CREATE TABLE e2e_i18n (
    id     INTEGER      NOT NULL,
    label  VARCHAR(40)  NOT NULL,
    sample STRING,
    CONSTRAINT pk_e2e_i18n PRIMARY KEY (id)
);

INSERT INTO e2e_i18n (id, label, sample) VALUES (1,  'ascii',     'ASCII Az 09');
INSERT INTO e2e_i18n (id, label, sample) VALUES (2,  'latin1',    'café Zürich Ångström Señor');
INSERT INTO e2e_i18n (id, label, sample) VALUES (3,  'latin-ext', 'Łódź Việt Nam İstanbul');
INSERT INTO e2e_i18n (id, label, sample) VALUES (4,  'greek',     'Ελληνικά αβγδ ΩΨ');
INSERT INTO e2e_i18n (id, label, sample) VALUES (5,  'cyrillic',  'Привет мир');
INSERT INTO e2e_i18n (id, label, sample) VALUES (6,  'hangul',    '한국어 가나다라 뷁');
INSERT INTO e2e_i18n (id, label, sample) VALUES (7,  'japanese',  'ひらがな カタカナ 日本語');
INSERT INTO e2e_i18n (id, label, sample) VALUES (8,  'chinese',   '简体字 繁體字 漢字');
INSERT INTO e2e_i18n (id, label, sample) VALUES (9,  'arabic',    'مرحبا بالعالم');
INSERT INTO e2e_i18n (id, label, sample) VALUES (10, 'hebrew',    'שלום עולם');
INSERT INTO e2e_i18n (id, label, sample) VALUES (11, 'thai',      'สวัสดีชาวโลก');
INSERT INTO e2e_i18n (id, label, sample) VALUES (12, 'symbols',   '— € £ ¥ ™ © ° ½ → « » …');
INSERT INTO e2e_i18n (id, label, sample) VALUES (13, 'emoji',     '😀 🚀 🌍 🇰🇷');
INSERT INTO e2e_i18n (id, label, sample) VALUES (14, 'mixed',     'A α 가 あ 中 Я ع 😀');
