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
package com.cubrid.cubridmigration.core.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.common.configuration.jdbc.IJDBCDriverChangedObserver;
import com.cubrid.common.configuration.jdbc.IJDBCDriverChangedSubject;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.testutil.ClassLoaderManagerState;
import com.cubrid.cubridmigration.testutil.DatabaseTypeDrivers;
import com.cubrid.cubridmigration.testutil.DriverJars;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@DisplayName("JDBCDriverManager")
class JDBCDriverManagerTest {

    private static final String CUBRID_DRIVER = "cubrid.jdbc.driver.CUBRIDDriver";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    private final List<String> events = new ArrayList<>();
    private final IJDBCDriverChangedObserver recorder =
            new IJDBCDriverChangedObserver() {
                @Override
                public void afterAdd(IJDBCDriverChangedSubject subject, String driverFile) {
                    events.add("add " + driverFile);
                }

                @Override
                public void afterDelete(IJDBCDriverChangedSubject subject, String driverFile) {
                    events.add("delete " + driverFile);
                }
            };

    private DatabaseTypeDrivers drivers;
    private ClassLoaderManagerState loaders;
    private JDBCDriverManager manager;

    @BeforeEach
    void newManager() throws Exception {
        drivers = DatabaseTypeDrivers.capture();
        loaders = ClassLoaderManagerState.capture();
        Constructor<JDBCDriverManager> constructor =
                JDBCDriverManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        manager = constructor.newInstance();
        manager.addObservor(recorder);
    }

    @AfterEach
    void restoreDrivers() {
        drivers.restore();
        loaders.restore();
    }

    @Nested
    @DisplayName("addObservor()")
    class AddObservor {

        @Test
        @DisplayName("an observer added twice is told once")
        void sameObserverTwice_isToldOnce(@TempDir Path dir) throws Exception {
            String jar = DriverJars.write(dir.resolve("cubrid.jar"), CUBRID_DRIVER).toString();

            manager.addObservor(recorder);
            manager.addDriver(jar, false);

            assertThat(events).containsExactly("add " + jar);
        }
    }

    @Nested
    @DisplayName("addDriver()")
    class AddDriver {

        @Test
        @DisplayName("a driver jar is registered for its type and reported")
        void driverJar_isRegisteredAndReported(@TempDir Path dir) throws Exception {
            String jar = DriverJars.write(dir.resolve("cubrid.jar"), CUBRID_DRIVER).toString();

            assertThat(manager.addDriver(jar, false)).isTrue();
            assertThat(DatabaseType.CUBRID.getJDBCData(jar)).isNotNull();
            assertThat(events).containsExactly("add " + jar);
        }

        @Test
        @DisplayName("a jar holding two drivers goes to the type that comes first")
        void jarWithTwoDrivers_goesToTheEarlierType(@TempDir Path dir) throws Exception {
            String jar =
                    DriverJars.write(dir.resolve("both.jar"), MYSQL_DRIVER, CUBRID_DRIVER)
                            .toString();

            manager.addDriver(jar, true);

            assertThat(DatabaseType.MYSQL.getJDBCData(jar)).isNotNull();
            assertThat(DatabaseType.CUBRID.getJDBCData(jar)).isNull();
        }

        @Test
        @DisplayName("in silence the observers are not told")
        void silence_skipsTheObservers(@TempDir Path dir) throws Exception {
            String jar = DriverJars.write(dir.resolve("cubrid.jar"), CUBRID_DRIVER).toString();

            assertThat(manager.addDriver(jar, true)).isTrue();
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("a driver already registered is refused")
        void registeredDriver_isRefused(@TempDir Path dir) throws Exception {
            String jar = DriverJars.write(dir.resolve("cubrid.jar"), CUBRID_DRIVER).toString();
            manager.addDriver(jar, true);
            int registered = DatabaseType.CUBRID.getJDBCDatas().size();

            assertThat(manager.addDriver(jar, false)).isFalse();
            assertThat(DatabaseType.CUBRID.getJDBCDatas()).hasSize(registered);
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("a jar with no known driver is refused")
        void jarWithoutDriver_isRefused(@TempDir Path dir) throws Exception {
            String jar = DriverJars.write(dir.resolve("plain.jar"), "not.a.Driver").toString();

            assertThat(manager.addDriver(jar, false)).isFalse();
            assertThat(events).isEmpty();
        }
    }

    @Nested
    @DisplayName("isDriverDuplicated()")
    class IsDriverDuplicated {

        @Test
        @DisplayName("without a type any registration counts")
        void noType_findsTheDriverInAnyType(@TempDir Path dir) throws Exception {
            String jar = DriverJars.write(dir.resolve("cubrid.jar"), CUBRID_DRIVER).toString();
            manager.addDriver(jar, true);

            assertThat(manager.isDriverDuplicated(null, jar)).isTrue();
        }

        @Test
        @DisplayName("with a type only its own registrations count")
        void type_findsOnlyItsOwnDriver(@TempDir Path dir) throws Exception {
            String jar = DriverJars.write(dir.resolve("cubrid.jar"), CUBRID_DRIVER).toString();
            manager.addDriver(jar, true);

            assertThat(manager.isDriverDuplicated(DatabaseType.CUBRID, jar)).isTrue();
            assertThat(manager.isDriverDuplicated(DatabaseType.MYSQL, jar)).isFalse();
        }

        @Test
        @DisplayName("a driver never registered is not a duplicate")
        void unregisteredDriver_isNotADuplicate(@TempDir Path dir) {
            assertThat(manager.isDriverDuplicated(null, dir.resolve("none.jar").toString()))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("deleteDriver()")
    class DeleteDriver {

        @Test
        @DisplayName("a registered driver is removed and reported with the path given")
        void registeredDriver_isRemovedAndReported(@TempDir Path dir) throws Exception {
            String jar = DriverJars.write(dir.resolve("cubrid.jar"), CUBRID_DRIVER).toString();
            manager.addDriver(jar, true);

            manager.deleteDriver(jar, false);

            assertThat(DatabaseType.CUBRID.getJDBCData(jar)).isNull();
            assertThat(events).containsExactly("delete " + jar);
        }

        @Test
        @DisplayName("only the first type holding the driver loses it")
        void onlyTheFirstType_losesTheDriver(@TempDir Path dir) throws Exception {
            String jar =
                    DriverJars.write(dir.resolve("both.jar"), MYSQL_DRIVER, CUBRID_DRIVER)
                            .toString();
            DatabaseType.MYSQL.addJDBCData(jar);
            DatabaseType.CUBRID.addJDBCData(jar);

            manager.deleteDriver(jar, true);

            assertThat(DatabaseType.MYSQL.getJDBCData(jar)).isNull();
            assertThat(DatabaseType.CUBRID.getJDBCData(jar)).isNotNull();
        }

        @Test
        @DisplayName("in silence the observers are not told")
        void silence_skipsTheObservers(@TempDir Path dir) throws Exception {
            String jar = DriverJars.write(dir.resolve("cubrid.jar"), CUBRID_DRIVER).toString();
            manager.addDriver(jar, true);

            manager.deleteDriver(jar, true);

            assertThat(DatabaseType.CUBRID.getJDBCData(jar)).isNull();
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("a driver never registered changes nothing")
        void unregisteredDriver_changesNothing(@TempDir Path dir) {
            List<JDBCData> before = JDBCUtil.getAllJDBCData();

            manager.deleteDriver(dir.resolve("none.jar").toString(), false);

            assertThat(JDBCUtil.getAllJDBCData()).containsExactlyElementsOf(before);
            assertThat(events).isEmpty();
        }
    }
}
