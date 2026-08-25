/*
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */
package com.cubrid.cubridmigration.informix;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("InformixDataTypeHelper")
class InformixDataTypeHelperTest {

    private static final InformixDataTypeHelper HELPER = InformixDataTypeHelper.getInstance(null);

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("any version -> the same singleton")
        void anyVersion_returnsSameInstance() {
            // The version argument is accepted but never used, Informix has one helper only.
            assertThat(InformixDataTypeHelper.getInstance(null))
                    .isSameAs(InformixDataTypeHelper.getInstance("14.10"));
        }
    }

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("-> DatabaseType.INFORMIX")
        void always_returnsInformixDatabaseType() {
            assertThat(HELPER.getDBType()).isSameAs(DatabaseType.INFORMIX);
        }
    }

    /**
     * No production code calls this method with an Informix helper: the only two call sites are
     * {@code OracleSchemaFetcher:596} and {@code TiberoSchemaFetcher:360}, each with its own
     * helper. The rows below pin the public contract of the method, not a migration path.
     */
    @Nested
    @DisplayName("getJdbcDataTypeID()")
    class GetJdbcDataTypeID {

        @Test
        @DisplayName("supported type -> its jdbc type id from the catalog")
        void supportedType_returnsJdbcTypeIdFromCatalog() {
            Catalog catalog = createCatalogWithSupportedType("varchar", Types.VARCHAR);

            assertThat(HELPER.getJdbcDataTypeID(catalog, "varchar", 255, null))
                    .isEqualTo(Types.VARCHAR);
        }

        @Test
        @DisplayName("precision and scale are ignored when the type has one candidate")
        void unambiguousType_ignoresPrecisionAndScale() {
            // The lookup key is the raw type name, so p/s never influence the result.
            Catalog catalog = createCatalogWithSupportedType("decimal", Types.DECIMAL);

            assertThat(HELPER.getJdbcDataTypeID(catalog, "decimal", null, null))
                    .isEqualTo(Types.DECIMAL);
            assertThat(HELPER.getJdbcDataTypeID(catalog, "decimal", 10, 2))
                    .isEqualTo(Types.DECIMAL);
        }

        @Test
        @DisplayName("upper case type name -> IllegalArgumentException")
        void upperCaseTypeName_throwsIllegalArgumentException() {
            // DEFECT: the raw data type is used as the map key with no case folding, so only an
            // exact key match resolves - see InformixDataTypeHelper.java:73. The keys are the
            // driver's TYPE_NAME values, stored verbatim
            // - see AbstractJDBCSchemaFetcher.java:1197,1241
            Catalog catalog = createCatalogWithSupportedType("varchar", Types.VARCHAR);

            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "VARCHAR", 255, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported Informix data type(VARCHAR)");
        }

        @Test
        @DisplayName("type name carrying its precision -> IllegalArgumentException")
        void typeNameWithPrecision_throwsIllegalArgumentException() {
            // DEFECT: getMainDataType() is not applied to the key either, so a name that carries
            // its arguments never matches a catalog key - see InformixDataTypeHelper.java:73
            Catalog catalog = createCatalogWithSupportedType("varchar", Types.VARCHAR);

            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "varchar(255)", 255, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported Informix data type(varchar(255))");
        }

        @Test
        @DisplayName("unknown type -> IllegalArgumentException")
        void unknownType_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(new Catalog(), "bson", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported Informix data type(bson)");
        }

        @Test
        @DisplayName("null type -> IllegalArgumentException")
        void nullDataType_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(new Catalog(), null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported Informix data type(null)");
        }

        @Test
        @DisplayName("empty type -> IllegalArgumentException")
        void emptyDataType_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(new Catalog(), "", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported Informix data type()");
        }

        @Test
        @DisplayName("two candidates for one type -> IllegalArgumentException")
        void ambiguousType_throwsIllegalArgumentException() {
            // Only a single candidate is resolved, precision and scale are never used to pick
            // one, they are merely echoed in the message (which has a double space).
            Catalog catalog = new Catalog();
            Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
            supported.put(
                    "decimal",
                    Arrays.asList(
                            createDataType("decimal", Types.DECIMAL),
                            createDataType("decimal", Types.NUMERIC)));
            catalog.setSupportedDataType(supported);

            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "decimal", 10, 2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported  Informix data type(decimal: p=10, s=2)");
        }

        @Test
        @DisplayName("empty candidate list -> IllegalArgumentException")
        void emptyCandidateList_throwsIllegalArgumentException() {
            // DEFECT: only size() == 1 is resolved, so an empty list is not distinguished from an
            // ambiguous one and the message blames the precision and scale of a type that has no
            // candidate at all - see InformixDataTypeHelper.java:81
            Catalog catalog = new Catalog();
            Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
            supported.put("varchar", new ArrayList<DataType>());
            catalog.setSupportedDataType(supported);

            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "varchar", 255, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported  Informix data type(varchar: p=255, s=null)");
        }

        private Catalog createCatalogWithSupportedType(String key, int jdbcTypeId) {
            Catalog catalog = new Catalog();
            Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
            supported.put(key, Arrays.asList(createDataType(key, jdbcTypeId)));
            catalog.setSupportedDataType(supported);
            return catalog;
        }

        private DataType createDataType(String typeName, int jdbcTypeId) {
            DataType dataType = new DataType();
            dataType.setTypeName(typeName);
            dataType.setJdbcDataTypeID(jdbcTypeId);
            return dataType;
        }
    }

    @Nested
    @DisplayName("getShownDataType()")
    class GetShownDataType {

        @ParameterizedTest(name = "[{index}] {0}(p={1}, s={2}) -> \"{3}\"")
        @CsvSource(
                nullValues = "null",
                value = {
                    // String types get their precision, from isGenericString() at
                    // InformixDataTypeHelper.java:103.
                    "char,          10,     null,   char(10)",
                    "varchar,       255,    null,   varchar(255)",
                    "nchar,         10,     null,   nchar(10)",
                    "nvarchar,      20,     null,   nvarchar(20)",

                    // "serial" is the only reachable member of the model list at
                    // InformixDataTypeHelper.java:105: char/varchar/nchar in that list are
                    // already matched at line 103. serial8/bigserial are in no list at all.
                    "serial,        10,     0,      serial(10)",
                    "serial8,       10,     0,      serial8",
                    "bigserial,     10,     0,      bigserial",

                    // Exact numeric types get precision and scale.
                    "decimal,       10,     2,      'decimal(10,2)'",
                    "numeric,       10,     2,      'numeric(10,2)'",
                    "money,         16,     2,      'money(16,2)'",
                    "decimal,       10,     null,   'decimal(10,0)'",

                    // Everything else falls through unchanged at
                    // InformixDataTypeHelper.java:110. "text" and "xml" also match the first
                    // branch at line 101 - copied from MSSQLDataTypeHelper.java:192, where both
                    // are real types - but that branch returns the type name as well, so no
                    // input can tell the two apart.
                    "text,          null,   null,   text",
                    "text,          10,     2,      text",
                    "xml,           null,   null,   xml",
                    "int,           10,     0,      int",
                    "integer,       10,     0,      integer",
                    "bigint,        19,     0,      bigint",
                    "int8,          19,     0,      int8",
                    "smallint,      5,      0,      smallint",
                    "float,         15,     null,   float",
                    "smallfloat,    7,      null,   smallfloat",
                    "boolean,       1,      null,   boolean",
                    "byte,          255,    null,   byte",
                    "bson,          null,   null,   bson",
                    "json,          null,   null,   json",
                    "blob,          null,   null,   blob",
                    "clob,          null,   null,   clob",
                    "date,          null,   null,   date",
                    "datetime,      null,   null,   datetime",
                    "interval,      null,   null,   interval",
                    "set,           null,   null,   set",
                    "list,          null,   null,   list",
                    "multiset,      null,   null,   multiset",
                })
        void informixDataTypes_renderShownDataType(
                String dataType, Integer precision, Integer scale, String expected) {
            assertThat(HELPER.getShownDataType(createColumn(dataType, precision, scale)))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("upper case type name -> case is preserved, precision still appended")
        void upperCaseTypeName_keepsCaseAndAppendsPrecision() {
            // Unlike getJdbcDataTypeID(), the classification here is case insensitive because it
            // goes through checkType(), which lower cases the input - see DBDataTypeHelper.java:83
            assertThat(HELPER.getShownDataType(createColumn("VARCHAR", 255, null)))
                    .isEqualTo("VARCHAR(255)");
        }

        @Test
        @DisplayName("lvarchar -> precision is lost")
        void lvarcharWithPrecision_dropsPrecision() {
            // DEFECT: lvarchar is a supported Informix source type with a length - see
            // INFORMIX2CUBRID.xml:130-131, which maps lvarchar(n) to varchar(n) - but it is in
            // none of the model lists, so it falls through and the length is dropped
            // - see InformixDataTypeHelper.java:110
            assertThat(HELPER.getShownDataType(createColumn("lvarchar", 2048, null)))
                    .isEqualTo("lvarchar");
            // The length only survives when it is already part of the name, and the only caller
            // takes the name from ResultSetMetaData.getColumnTypeName(), which carries no
            // arguments - see AbstractJDBCSchemaFetcher.java:466, called from
            // InformixSchemaFetcher.java:120,124
            assertThat(HELPER.getShownDataType(createColumn("lvarchar(2048)", 2048, null)))
                    .isEqualTo("lvarchar(2048)");
        }

        @Test
        @DisplayName("null data type -> empty string")
        void nullDataType_returnsEmptyString() {
            // Line 97 codes for a null data type explicitly, but the state cannot survive in
            // production: the only caller dereferences column.getDataType() one line after
            // storing the result - see InformixSchemaFetcher.java:124-125
            assertThat(HELPER.getShownDataType(createColumn(null, 255, null))).isEmpty();
        }

        @Test
        @DisplayName("empty data type -> empty string")
        void emptyDataType_returnsEmptyString() {
            assertThat(HELPER.getShownDataType(createColumn("", 255, null))).isEmpty();
        }

        @Test
        @DisplayName("blank data type -> empty string")
        void blankDataType_returnsEmptyString() {
            assertThat(HELPER.getShownDataType(createColumn("   ", 255, null))).isEmpty();
        }

        @Test
        @DisplayName("surrounding whitespace -> trimmed name with precision")
        void surroundingWhitespace_returnsTrimmedName() {
            assertThat(HELPER.getShownDataType(createColumn(" varchar ", 255, null)))
                    .isEqualTo("varchar(255)");
        }

        @Test
        @DisplayName("missing precision -> zero is rendered")
        void missingPrecision_rendersZeroPrecision() {
            // Column.getPrecision() maps null to 0 - see Column.java:199. The only caller never
            // supplies null: buildSQLTable() clamps a non-positive precision to 1
            // - see AbstractJDBCSchemaFetcher.java:467-468
            assertThat(HELPER.getShownDataType(createColumn("varchar", null, null)))
                    .isEqualTo("varchar(0)");
        }

        @Test
        @DisplayName("data type already carrying its precision -> precision appended twice")
        void dataTypeWithArguments_appendsPrecisionTwice() {
            // The arguments of the incoming data type are not stripped with getMainDataType(), so
            // the precision is appended a second time - see InformixDataTypeHelper.java:104. Not
            // reachable from the only caller, which passes the argument-free
            // ResultSetMetaData.getColumnTypeName() - see AbstractJDBCSchemaFetcher.java:466
            assertThat(HELPER.getShownDataType(createColumn("varchar(255)", 255, null)))
                    .isEqualTo("varchar(255)(255)");
        }

        @Test
        @DisplayName("\"identity\" inside the type name -> removed")
        void identityInTypeName_isRemoved() {
            // The removal is inherited from the MSSQL helper this method was copied from - see
            // MSSQLDataTypeHelper.java:191. No Informix source type carries "identity".
            assertThat(HELPER.getShownDataType(createColumn("varchar identity", 255, null)))
                    .isEqualTo("varchar(255)");
        }

        @Test
        @DisplayName("\"identity\" glued to the type name -> still removed")
        void identityGluedToTypeName_isRemoved() {
            // "identity" is removed as a plain substring, not as a word
            // - see InformixDataTypeHelper.java:100
            assertThat(HELPER.getShownDataType(createColumn("identityvarchar", 255, null)))
                    .isEqualTo("varchar(255)");
        }

        @Test
        @DisplayName("type name that is only \"identity\" -> empty string")
        void typeNameThatIsOnlyIdentity_returnsEmptyString() {
            assertThat(HELPER.getShownDataType(createColumn("identity", 10, 0))).isEmpty();
        }

        @Test
        @DisplayName("datetime with a qualifier -> unchanged")
        void datetimeWithQualifier_returnsTypeNameUnchanged() {
            // The helper does not normalize the qualifier, InformixSchemaFetcher rewrites the
            // column to plain "datetime" afterwards - see InformixSchemaFetcher.java:125-128
            assertThat(HELPER.getShownDataType(createColumn("datetime year to fraction", 5, 5)))
                    .isEqualTo("datetime year to fraction");
        }
    }

    @Nested
    @DisplayName("isBinary()")
    class IsBinary {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    // INFORMIX_BIN_TYPES is "/byte/bson/blob/" - see
                    // InformixDataTypeHelper.java:51
                    "byte,           true",
                    "BYTE,           true",
                    "byte(255),      true",
                    "bson,           true",
                    "BSON,           true",
                    "blob,           true",
                    "BLOB,           true",
                    // clob and text are Informix LOB types, but they are not in that list.
                    "clob,           false",
                    "text,           false",
                    // json has its own export handler - see InformixExportHelper.java:59 - but it
                    // is not in that list either.
                    "json,           false",
                    "boolean,        false",
                    "char,           false",
                    "varchar,        false",
                    "lvarchar,       false",
                    // The override ignores the base class' BINARY_TYPES names entirely.
                    "bit,            false",
                    "binary,         false",
                    "varbinary,      false",
                    "set,            false",
                    "null,           false",
                    "'',             false",
                })
        void variousDataTypes_classifyInformixBinaryTypes(String dataType, boolean expected) {
            assertThat(HELPER.isBinary(dataType)).isEqualTo(expected);
        }

        @Test
        @DisplayName("surrounding whitespace -> true")
        void surroundingWhitespace_returnsTrue() {
            assertThat(HELPER.isBinary(" byte ")).isTrue();
        }
    }

    @Nested
    @DisplayName("isCollection()")
    class IsCollection {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @CsvSource(
                nullValues = "null",
                value = {
                    "set",
                    "SET",
                    "set(int)",
                    "list",
                    "multiset",
                    "varchar",
                    "null",
                })
        void everyDataType_returnsFalse(String dataType) {
            // DEFECT: the implementation is commented out and false is hard coded, so the
            // Informix collection types set/list/multiset are never recognized even though
            // INFOMRIX_COLLECTION_TYPES lists them and InformixExportHelper registers a handler
            // for each of them - see InformixDataTypeHelper.java:122 and
            // InformixExportHelper.java:62-64. Latent today: no production code passes an
            // Informix helper to isCollection() or to the inherited parseDTInstance(), the only
            // consumer of it inside the base class - see DBDataTypeHelper.java:341
            assertThat(HELPER.isCollection(dataType)).isFalse();
        }
    }

    @Nested
    @DisplayName("isEnum()")
    class IsEnum {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "enum,            true",
                    "ENUM,            true",
                    "'enum(a,b)',     true",
                    "enumeration,     false",
                    "varchar,         false",
                    "null,            false",
                    "'',              false",
                })
        void variousDataTypes_classifyEnumType(String dataType, boolean expected) {
            // The override at InformixDataTypeHelper.java:126 repeats the body of
            // DBDataTypeHelper.isEnum() - see DBDataTypeHelper.java:162-164 - so it changes
            // nothing. No production code calls isEnum() on an Informix helper either, so the
            // rows below pin the public contract, not a migration path.
            assertThat(HELPER.isEnum(dataType)).isEqualTo(expected);
        }
    }
}
