package com.cubrid.cubridmigration.tibero;

import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TiberoDataTypeHelper")
public class TiberoDataTypeHelperTest {

    @Nested
    @DisplayName("getTiberoDataTypeKey()")
    class GetTiberoDataTypeKey {

        @Test
        @DisplayName("null -> emply String")
        void null_returnEmptyString() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey(null)).isEmpty();
        }

        @Test
        @DisplayName("empty string -> empty string")
        void emptyString_returnEmptyString() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey("")).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
        @CsvSource({
            // No pattern: keep as is with uppercase normalization.
            "VARCHAR2,             VARCHAR2",
            "NUMBER,               NUMBER",
            "CLOB,                 CLOB",
            "BLOB,                 BLOB",
            "CHAR,                 CHAR",
            "NCHAR,                NCHAR",
            "NVARCHAR2,            NVARCHAR2",
            "DATE,                 DATE",
            "ROWID,                ROWID",
            "RAW,                  RAW",
            "LONG,                 LONG",
            "LONG RAW,             LONG RAW",
            "BINARY_FLOAT,         BINARY_FLOAT",
            "BINARY_DOUBLE,        BINARY_DOUBLE",

            // TIMESTAMP: strip precision.
            "TIMESTAMP,            TIMESTAMP",
            "TIMESTAMP(0),         TIMESTAMP",
            "TIMESTAMP(6),         TIMESTAMP",
            "TIMESTAMP(9),         TIMESTAMP",

            // TIME: strip precision.
            "TIME,                 TIME",
            "TIME(0),              TIME",
            "TIME(6),              TIME",

            // TIMESTAMP WITH TIME ZONE pattern.
            "TIMESTAMP WITH TIME ZONE,              TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP(6) WITH TIME ZONE,           TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP(0) WITH TIME ZONE,           TIMESTAMP WITH TIME ZONE",

            // TIMESTAMP WITH LOCAL TIME ZONE pattern.
            "TIMESTAMP WITH LOCAL TIME ZONE,        TIMESTAMP WITH LOCAL TIME ZONE",
            "TIMESTAMP(6) WITH LOCAL TIME ZONE,     TIMESTAMP WITH LOCAL TIME ZONE",
            "TIMESTAMP(3) WITH LOCAL TIME ZONE,     TIMESTAMP WITH LOCAL TIME ZONE",

            // INTERVAL DAY TO SECOND pattern.
            "INTERVAL DAY TO SECOND,                INTERVAL DAY TO SECOND",
            "INTERVAL DAY(2) TO SECOND(6),          INTERVAL DAY TO SECOND",
            "INTERVAL DAY(5) TO SECOND(9),          INTERVAL DAY TO SECOND",
            "INTERVAL DAY(0) TO SECOND(0),          INTERVAL DAY TO SECOND",

            // INTERVAL YEAR TO MONTH pattern.
            "INTERVAL YEAR TO MONTH,                INTERVAL YEAR TO MONTH",
            "INTERVAL YEAR(4) TO MONTH,             INTERVAL YEAR TO MONTH",
            "INTERVAL YEAR(2) TO MONTH,             INTERVAL YEAR TO MONTH",

            // Special cases.
            "JSON,                 JSON",
            "XMLTYPE,              XMLTYPE",
        })
        void variousTypes_returnsNormalizedKey(String input, String expected) {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey(input.trim()))
                    .isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("lowercase input -> uppercase")
        void lowercaseInput_returnUppercase() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey("varchar2"))
                    .isEqualTo("VARCHAR2");
        }

        @Test
        @DisplayName("extra spaces -> single space")
        void extraWhitespace_normalizedToSingleSpace() {
            assertThat(TiberoDataTypeHelper.getTiberoDataTypeKey("TIMESTAMP  WITH  TIME  ZONE"))
                    .isEqualTo("TIMESTAMP WITH TIME ZONE");
        }
    }

    @Nested
    @DisplayName("isBinary()")
    class IsBianry {

        @Test
        @DisplayName("blob lowercase -> true")
        void blob_lowercase_returnsTrue() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("blob")).isTrue();
        }

        @Test
        @DisplayName("BLOB uppercase -> true")
        void blob_uppercase_returnTrue() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("BLOB")).isTrue();
        }

        @Test
        @DisplayName("CLOB -> false for text type")
        void clob_returnFalse() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("CLOB")).isFalse();
        }

        @Test
        @DisplayName("RAW -> false in Tibero")
        void raw_returnsFalse() {
            assertThat(TiberoDataTypeHelper.getInstance(null).isBinary("RAW")).isFalse();
        }
    }

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance")
        void singleton_returnSameInstance() {
            assertThat(TiberoDataTypeHelper.getInstance(null))
                    .isSameAs(TiberoDataTypeHelper.getInstance("1.0"));
        }
    }

    @Nested
    @DisplayName("getJdbcDataTypeID()")
    class GetJdbcDataTypeID {

        @Test
        @DisplayName("NUMBER -> delegates to number type mapper")
        void number_returnsJdbcTypeFromPrecisionAndScale() {
            Integer jdbcType =
                    TiberoDataTypeHelper.getInstance(null)
                            .getJdbcDataTypeID(new Catalog(), "NUMBER", 10, 0);

            assertThat(jdbcType).isEqualTo(Types.INTEGER);
        }

        @Test
        @DisplayName("unsupported ROWID -> null")
        void unsupportedRowid_returnsNull() {
            Integer jdbcType =
                    TiberoDataTypeHelper.getInstance(null)
                            .getJdbcDataTypeID(new Catalog(), "ROWID", null, null);

            assertThat(jdbcType).isNull();
        }

        @Test
        @DisplayName("fixed type BINARY_FLOAT -> FLOAT")
        void fixedType_returnsFixedJdbcType() {
            Integer jdbcType =
                    TiberoDataTypeHelper.getInstance(null)
                            .getJdbcDataTypeID(new Catalog(), "BINARY_FLOAT", null, null);

            assertThat(jdbcType).isEqualTo(Types.FLOAT);
        }

        @Test
        @DisplayName("dynamic VARCHAR2 -> jdbc type from supported data types")
        void dynamicType_returnsJdbcTypeFromCatalogMap() {
            Catalog catalog = createCatalogWithSupportedType("VARCHAR2", Types.VARCHAR);

            Integer jdbcType =
                    TiberoDataTypeHelper.getInstance(null)
                            .getJdbcDataTypeID(catalog, "VARCHAR2", 20, null);

            assertThat(jdbcType).isEqualTo(Types.VARCHAR);
        }

        @Test
        @DisplayName("normalized TIMESTAMP(6) -> jdbc type from TIMESTAMP key")
        void normalizedDynamicType_usesNormalizedLookupKey() {
            Catalog catalog = createCatalogWithSupportedType("TIMESTAMP", Types.TIMESTAMP);

            Integer jdbcType =
                    TiberoDataTypeHelper.getInstance(null)
                            .getJdbcDataTypeID(catalog, "TIMESTAMP(6)", null, 6);

            assertThat(jdbcType).isEqualTo(Types.TIMESTAMP);
        }

        @Test
        @DisplayName("missing supported type -> IllegalArgumentException")
        void missingSupportedType_throwsIllegalArgumentException() {
            Catalog catalog = new Catalog();

            assertThatThrownBy(
                            () ->
                                    TiberoDataTypeHelper.getInstance(null)
                                            .getJdbcDataTypeID(catalog, "VARCHAR2", 10, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not supported Tibero data type(VARCHAR2)");
        }

        private Catalog createCatalogWithSupportedType(String key, int jdbcTypeId) {
            Catalog catalog = new Catalog();
            DataType dataType = new DataType();
            dataType.setTypeName(key);
            dataType.setJdbcDataTypeID(jdbcTypeId);

            Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
            supported.put(key, Arrays.asList(dataType));
            catalog.setSupportedDataType(supported);
            return catalog;
        }
    }
}
