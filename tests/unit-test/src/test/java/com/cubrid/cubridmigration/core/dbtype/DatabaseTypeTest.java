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
 * - Neither the name of the copyright holder nor the names of its contributors
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
package com.cubrid.cubridmigration.core.dbtype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.cubrid.cubridmigration.core.connection.JDBCData;
import com.cubrid.cubridmigration.core.datatype.DBDataTypeHelper;
import com.cubrid.cubridmigration.core.sql.SQLHelper;
import com.cubrid.cubridmigration.testutil.ClassLoaderManagerState;
import com.cubrid.cubridmigration.testutil.DriverJars;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@DisplayName("DatabaseType")
class DatabaseTypeTest {

    private ClassLoaderManagerState loaders;

    @BeforeEach
    void captureLoaders() {
        loaders = ClassLoaderManagerState.capture();
    }

    @AfterEach
    void restoreLoaders() {
        loaders.restore();
    }

    private static DatabaseType stubType(String... jdbcClasses) {
        return new DatabaseType(99, "stub", jdbcClasses, "0", null, null, null, false) {
            @Override
            public SQLHelper getSQLHelper(String version) {
                return null;
            }

            @Override
            public DBDataTypeHelper getDataTypeHelper(String version) {
                return null;
            }
        };
    }

    private static ClassLoader loaderFor(String... classNames) {
        List<String> names = Arrays.asList(classNames);
        return new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (names.contains(name)) {
                    return Object.class;
                }
                throw new ClassNotFoundException(name);
            }
        };
    }

    @Nested
    @DisplayName("getAllTypes()")
    class GetAllTypes {

        @Test
        @DisplayName("the seven types come in a fixed order")
        void allTypes_comeInAFixedOrder() {
            assertThat(DatabaseType.getAllTypes())
                    .containsExactly(
                            DatabaseType.MYSQL,
                            DatabaseType.CUBRID,
                            DatabaseType.ORACLE,
                            DatabaseType.MSSQL,
                            DatabaseType.MARIADB,
                            DatabaseType.INFORMIX,
                            DatabaseType.TIBERO);
        }

        @Test
        @DisplayName("changing the returned array leaves the list alone")
        void returnedArray_isACopy() {
            DatabaseType.getAllTypes()[0] = null;

            assertThat(DatabaseType.getAllTypes()[0]).isSameAs(DatabaseType.MYSQL);
        }
    }

    @Nested
    @DisplayName("getDatabaseTypeByID()")
    class GetDatabaseTypeByID {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("each ID gives its type")
        @CsvSource({
            "0, MYSQL",
            "1, CUBRID",
            "2, MSSQL",
            "3, ORACLE",
            "4, MARIADB",
            "5, INFORMIX",
            "6, TIBERO"
        })
        void id_givesItsType(int id, String name) {
            assertThat(DatabaseType.getDatabaseTypeByID(id).getName()).isEqualTo(name);
        }

        @Test
        @DisplayName("an unknown ID is rejected")
        void unknownId_throwsRuntimeException() {
            assertThatThrownBy(() -> DatabaseType.getDatabaseTypeByID(99))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database Type [99] is not supported!");
        }
    }

    @Nested
    @DisplayName("getDatabaseTypeIDByDBName()")
    class GetDatabaseTypeIDByDBName {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("a type name in any case gives its type")
        @CsvSource({
            "cubrid, CUBRID",
            "mysql, MYSQL",
            "oracle, ORACLE",
            "MySQL, MYSQL",
            "Tibero, TIBERO"
        })
        void nameInAnyCase_givesItsType(String name, String expected) {
            assertThat(DatabaseType.getDatabaseTypeIDByDBName(name).getName()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("an unknown or missing name is rejected")
        @ValueSource(strings = {"sqlserver"})
        @NullAndEmptySource
        void unknownName_throwsRuntimeException(String name) {
            assertThatThrownBy(() -> DatabaseType.getDatabaseTypeIDByDBName(name))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database Type [" + name + "] is not supported!");
        }
    }

    @Nested
    @DisplayName("DatabaseType()")
    class Constructor {

        @Test
        @DisplayName(
                "the driver class names are copied, so changing the array later changes nothing")
        void driverClassNames_areCopied(@TempDir Path dir) {
            String[] classNames = {"stub.Driver"};
            DatabaseType type = stubType(classNames);
            classNames[0] = "other.Driver";

            type.addJDBCData(
                    new String[] {dir.resolve("a.jar").toString()},
                    new ClassLoader[] {loaderFor("stub.Driver", "other.Driver")});

            assertThat(type.getJDBCDatas())
                    .extracting(JDBCData::getDriverClassName)
                    .containsExactly("stub.Driver");
        }
    }

    @Nested
    @DisplayName("addJDBCData()")
    class AddJDBCData {

        @Test
        @DisplayName("a jar is registered with the first of the driver classes it holds")
        void jar_isRegisteredWithTheFirstClassItHolds(@TempDir Path dir) throws Exception {
            DatabaseType type = stubType("stub.First", "stub.Second");
            Path jar = DriverJars.write(dir.resolve("driver.jar"), "stub.Second");

            assertThat(type.addJDBCData(jar.toString())).isTrue();
            assertThat(type.getJDBCDatas())
                    .singleElement()
                    .satisfies(
                            data -> {
                                assertThat(data.getDriverClassName()).isEqualTo("stub.Second");
                                assertThat(data.getJdbcDriverPath())
                                        .isEqualTo(jar.toFile().getCanonicalPath());
                            });
        }

        @Test
        @DisplayName("the same jar added twice is registered twice")
        void sameJarTwice_isRegisteredTwice(@TempDir Path dir) throws Exception {
            DatabaseType type = stubType("stub.Driver");
            Path jar = DriverJars.write(dir.resolve("driver.jar"), "stub.Driver");

            type.addJDBCData(jar.toString());
            type.addJDBCData(jar.toString());

            assertThat(type.getJDBCDatas()).hasSize(2);
        }

        @Test
        @DisplayName("a jar without any of the driver classes is not registered")
        void jarWithoutDriver_isNotRegistered(@TempDir Path dir) throws Exception {
            DatabaseType type = stubType("stub.Driver");
            Path jar = DriverJars.write(dir.resolve("other.jar"), "not.a.Driver");

            assertThat(type.addJDBCData(jar.toString())).isFalse();
            assertThat(type.getJDBCDatas()).isEmpty();
        }

        @Test
        @DisplayName("with class loaders each path gets the first driver class its loader finds")
        void eachPath_getsTheFirstClassItsLoaderFinds(@TempDir Path dir) {
            DatabaseType type = stubType("stub.First", "stub.Second");
            String first = dir.resolve("first.jar").toString();
            String second = dir.resolve("second.jar").toString();

            type.addJDBCData(
                    new String[] {first, second},
                    new ClassLoader[] {
                        loaderFor("stub.Second"), loaderFor("stub.First", "stub.Second")
                    });

            assertThat(type.getJDBCDatas())
                    .extracting(JDBCData::getJdbcDriverName, JDBCData::getDriverClassName)
                    .containsExactly(
                            tuple("first.jar", "stub.Second"), tuple("second.jar", "stub.First"));
        }

        @Test
        @DisplayName("with class loaders a path already registered or without a driver is skipped")
        void registeredOrDriverlessPath_isSkipped(@TempDir Path dir) {
            DatabaseType type = stubType("stub.Driver");
            String registered = dir.resolve("registered.jar").toString();
            type.addJDBCData(
                    new String[] {registered}, new ClassLoader[] {loaderFor("stub.Driver")});

            type.addJDBCData(
                    new String[] {registered, dir.resolve("none.jar").toString()},
                    new ClassLoader[] {loaderFor("stub.Driver"), loaderFor("not.a.Driver")});

            assertThat(type.getJDBCDatas())
                    .extracting(JDBCData::getJdbcDriverName)
                    .containsExactly("registered.jar");
        }

        @Test
        @DisplayName("with class loaders only as many paths as loaders are tried")
        void onlyAsManyPathsAsLoaders_areTried(@TempDir Path dir) {
            DatabaseType type = stubType("stub.Driver");

            type.addJDBCData(
                    new String[] {dir.resolve("a.jar").toString(), dir.resolve("b.jar").toString()},
                    new ClassLoader[] {loaderFor("stub.Driver")});

            assertThat(type.getJDBCDatas())
                    .extracting(JDBCData::getJdbcDriverName)
                    .containsExactly("a.jar");
            assertThatThrownBy(
                            () ->
                                    type.addJDBCData(
                                            new String[0],
                                            new ClassLoader[] {loaderFor("stub.Driver")}))
                    .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("with class loaders a loader failure other than a missing class goes up")
        void otherLoaderFailure_propagates(@TempDir Path dir) {
            DatabaseType type = stubType("stub.Driver");
            ClassLoader failing =
                    new ClassLoader(null) {
                        @Override
                        protected Class<?> findClass(String name) {
                            throw new IllegalStateException("broken loader");
                        }
                    };

            assertThatThrownBy(
                            () ->
                                    type.addJDBCData(
                                            new String[] {dir.resolve("a.jar").toString()},
                                            new ClassLoader[] {failing}))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("getJDBCData()")
    class GetJDBCData {

        @Test
        @DisplayName("an existing file is looked up by its canonical path")
        void existingFile_isFoundByCanonicalPath(@TempDir Path dir) throws Exception {
            DatabaseType type = stubType("stub.Driver");
            Path jar = DriverJars.write(dir.resolve("driver.jar"), "stub.Driver");
            type.addJDBCData(jar.toString());

            assertThat(type.getJDBCData(dir.resolve("./driver.jar").toString()))
                    .isSameAs(type.getJDBCDatas().get(0));
        }

        @Test
        @DisplayName("an existing file with the same name in another folder is another driver")
        void sameNameInAnotherFolder_isAnotherDriver(@TempDir Path dir) throws Exception {
            DatabaseType type = stubType("stub.Driver");
            Path registered = Files.createDirectories(dir.resolve("a")).resolve("driver.jar");
            type.addJDBCData(DriverJars.write(registered, "stub.Driver").toString());
            Path other = Files.createDirectories(dir.resolve("b")).resolve("driver.jar");
            DriverJars.write(other, "stub.Driver");

            assertThat(type.getJDBCData(other.toString())).isNull();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("a blank path finds nothing")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        void blankPath_returnsNull(String path) {
            DatabaseType type = stubType("stub.Driver");
            type.addJDBCData(
                    new String[] {"driver.jar"}, new ClassLoader[] {loaderFor("stub.Driver")});

            assertThat(type.getJDBCData(path)).isNull();
        }

        @Test
        @DisplayName("a missing file's name found inside another driver's path gives that driver")
        void missingFileName_matchesInsideAnotherPath(@TempDir Path dir) {
            // DEFECT: a missing file is looked up by its name alone, and indexOf() takes any
            // registered path containing it, so cubrid.jar finds JDBC-11.2.1.0040-cubrid.jar
            // - see DatabaseType.getJDBCData()
            DatabaseType type = stubType("stub.Driver");
            String registered = dir.resolve("JDBC-11.2.1.0040-cubrid.jar").toString();
            type.addJDBCData(
                    new String[] {registered}, new ClassLoader[] {loaderFor("stub.Driver")});

            assertThat(type.getJDBCData("cubrid.jar"))
                    .extracting(JDBCData::getJdbcDriverName)
                    .isEqualTo("JDBC-11.2.1.0040-cubrid.jar");
        }

        @Test
        @DisplayName("an existing file whose path starts another driver's path gives that driver")
        void existingFilePath_matchesTheStartOfAnotherPath(@TempDir Path dir) throws Exception {
            // DEFECT: indexOf() also takes a registered path that merely contains the canonical
            // path, so a file named like the start of another jar finds that jar
            // - see DatabaseType.getJDBCData()
            DatabaseType type = stubType("stub.Driver");
            String registered = dir.resolve("JDBC-11.2.1.0040-cubrid.jar").toString();
            type.addJDBCData(
                    new String[] {registered}, new ClassLoader[] {loaderFor("stub.Driver")});
            Path shorter = Files.write(dir.resolve("JDBC-11.2.1.0040-cubrid"), new byte[0]);

            assertThat(type.getJDBCData(shorter.toString()))
                    .extracting(JDBCData::getJdbcDriverName)
                    .isEqualTo("JDBC-11.2.1.0040-cubrid.jar");
        }
    }

    @Nested
    @DisplayName("getJDBCDatas()")
    class GetJDBCDatas {

        @Test
        @DisplayName("changing the returned list leaves the registered drivers alone")
        void returnedList_isACopy(@TempDir Path dir) {
            DatabaseType type = stubType("stub.Driver");
            type.addJDBCData(
                    new String[] {dir.resolve("a.jar").toString()},
                    new ClassLoader[] {loaderFor("stub.Driver")});
            List<JDBCData> first = type.getJDBCDatas();

            first.clear();

            assertThat(type.getJDBCDatas()).hasSize(1);
        }

        @Test
        @DisplayName("the returned list holds the registered driver objects themselves")
        void returnedList_sharesTheDrivers(@TempDir Path dir) {
            DatabaseType type = stubType("stub.Driver");
            type.addJDBCData(
                    new String[] {dir.resolve("a.jar").toString()},
                    new ClassLoader[] {loaderFor("stub.Driver")});

            assertThat(type.getJDBCDatas().get(0)).isSameAs(type.getJDBCDatas().get(0));
        }
    }
}
