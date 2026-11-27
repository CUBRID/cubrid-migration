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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.testutil.ClassLoaderManagerState;
import com.cubrid.cubridmigration.testutil.DatabaseTypeDrivers;
import com.cubrid.cubridmigration.testutil.DriverJars;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipException;

@DisplayName("JDBCUtil")
class JDBCUtilTest {

    private DatabaseTypeDrivers drivers;
    private ClassLoaderManagerState loaders;
    private List<JDBCData> before;

    @BeforeEach
    void captureDrivers() {
        drivers = DatabaseTypeDrivers.capture();
        loaders = ClassLoaderManagerState.capture();
        before = JDBCUtil.getAllJDBCData();
    }

    @AfterEach
    void restoreDrivers() {
        drivers.restore();
        loaders.restore();
    }

    private List<String> added() {
        List<String> names = new ArrayList<>();
        for (JDBCData data : JDBCUtil.getAllJDBCData()) {
            if (!before.contains(data)) {
                names.add(data.getDatabaseType().getName() + ":" + data.getJdbcDriverName());
            }
        }
        return names;
    }

    @Nested
    @DisplayName("initialJdbcByPath()")
    class InitialJdbcByPath {

        @Test
        @DisplayName("every jar in the folder is registered for the types whose driver it holds")
        void driverJars_areRegisteredForTheirTypes(@TempDir Path dir) throws Exception {
            DriverJars.write(dir.resolve("cubrid.jar"), "cubrid.jdbc.driver.CUBRIDDriver");
            DriverJars.write(dir.resolve("mysql.jar"), "org.gjt.mm.mysql.Driver");
            DriverJars.write(dir.resolve("plain.jar"), "not.a.Driver");

            JDBCUtil.initialJdbcByPath(dir.toString());

            assertThat(added()).containsExactly("MYSQL:mysql.jar", "CUBRID:cubrid.jar");
        }

        @Test
        @DisplayName("a subfolder is skipped even when it holds a driver class")
        void subfolder_isSkipped(@TempDir Path dir) throws Exception {
            DriverJars.writeClassFolder(dir.resolve("classes"), "cubrid.jdbc.driver.CUBRIDDriver");

            JDBCUtil.initialJdbcByPath(dir.toString());

            assertThat(added()).isEmpty();
        }

        @Test
        @DisplayName("a missing folder is created and nothing is registered")
        void missingFolder_isCreated(@TempDir Path dir) {
            Path folder = dir.resolve("new/jdbc");

            JDBCUtil.initialJdbcByPath(folder.toString());

            assertThat(folder).isDirectory();
            assertThat(added()).isEmpty();
        }

        @Test
        @DisplayName("a folder that cannot be created registers nothing")
        void uncreatableFolder_registersNothing(@TempDir Path dir) throws Exception {
            Path blocker = Files.write(dir.resolve("blocker"), new byte[] {1});

            JDBCUtil.initialJdbcByPath(blocker.resolve("jdbc").toString());

            assertThat(added()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getJdbcJarVersion()")
    class GetJdbcJarVersion {

        private Path jar(Path file, String... entries) throws IOException {
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
                for (String entry : entries) {
                    out.putNextEntry(new JarEntry(entry));
                    out.closeEntry();
                }
            }
            return file;
        }

        @Test
        @DisplayName("the first entry named CUBRID-JDBC-... is the version")
        void firstVersionEntry_isTheVersion(@TempDir Path dir) throws Exception {
            Path file =
                    jar(
                            dir.resolve("JDBC.jar"),
                            "META-INF/",
                            "CUBRID-JDBC-9.0",
                            "CUBRID-JDBC-10.0");

            assertThat(JDBCUtil.getJdbcJarVersion(file.toString())).isEqualTo("CUBRID-JDBC-9.0");
        }

        @Test
        @DisplayName("a version entry inside a folder does not count")
        void versionEntryInAFolder_doesNotCount(@TempDir Path dir) throws Exception {
            Path file = jar(dir.resolve("JDBC.jar"), "x/CUBRID-JDBC-9.0");

            assertThat(JDBCUtil.getJdbcJarVersion(file.toString())).isNull();
        }

        @Test
        @DisplayName("a jar without a version entry gives null")
        void jarWithoutVersion_returnsNull(@TempDir Path dir) throws Exception {
            Path file = jar(dir.resolve("other.jar"), "a.txt");

            assertThat(JDBCUtil.getJdbcJarVersion(file.toString())).isNull();
        }

        @Test
        @DisplayName("a file that is not a jar is rejected")
        void fileThatIsNotAJar_throwsZipException(@TempDir Path dir) throws Exception {
            Path file =
                    Files.write(dir.resolve("text.jar"), "text".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> JDBCUtil.getJdbcJarVersion(file.toString()))
                    .isInstanceOf(ZipException.class);
        }

        @Test
        @DisplayName("a missing file is rejected")
        void missingFile_throwsIOException(@TempDir Path dir) {
            assertThatThrownBy(() -> JDBCUtil.getJdbcJarVersion(dir.resolve("none.jar").toString()))
                    .isInstanceOf(IOException.class);
        }
    }
}
