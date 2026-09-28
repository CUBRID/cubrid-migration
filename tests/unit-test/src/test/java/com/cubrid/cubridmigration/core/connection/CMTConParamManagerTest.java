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

import com.cubrid.common.configuration.jdbc.IJDBCConnecInfo;
import com.cubrid.common.configuration.jdbc.IJDBCConnectionChangedObserver;
import com.cubrid.common.configuration.jdbc.IJDBCInfoChangedSubject;
import com.cubrid.cubridmigration.core.common.CipherUtils;
import com.cubrid.cubridmigration.core.common.xml.IXMLMemento;
import com.cubrid.cubridmigration.core.common.xml.XMLMemento;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.SchemaCatalog;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@DisplayName("CMTConParamManager")
class CMTConParamManagerTest {

    private static final String PASSWORD = CipherUtils.encrypt("pw");

    private CMTConParamManager manager;
    private final Recorder recorder = new Recorder();

    @BeforeEach
    void newManager() throws Exception {
        Constructor<CMTConParamManager> constructor =
                CMTConParamManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        manager = constructor.newInstance();
    }

    private static ConnParameters connection(String name, int port) {
        return ConnParameters.getConParam(
                name,
                "localhost",
                port,
                "demodb",
                DatabaseType.CUBRID,
                "UTF-8",
                "dba",
                "pw",
                "driver.jar",
                null);
    }

    private List<String> names() {
        return manager.getConnections().stream()
                .map(ConnParameters::getConName)
                .collect(Collectors.toList());
    }

    private static Path file(Path dir, String... databases) throws IOException {
        return Files.write(
                dir.resolve("connections.xml"),
                ("<databases>" + String.join("", databases) + "</databases>")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static IXMLMemento[] saved(File file) throws IOException {
        return XMLMemento.loadMemento(file.getPath()).getChildren("database");
    }

    private static final class Recorder implements IJDBCConnectionChangedObserver {
        private final List<String> events = new ArrayList<>();
        private final List<IJDBCConnecInfo> received = new ArrayList<>();

        @Override
        public void afterAdd(IJDBCInfoChangedSubject initiator, IJDBCConnecInfo newCon) {
            events.add("add " + newCon.getConName());
            received.add(newCon);
        }

        @Override
        public void afterModify(
                IJDBCInfoChangedSubject initiator, IJDBCConnecInfo oldCon, IJDBCConnecInfo newCon) {
            events.add("modify " + oldCon.getConName() + " -> " + newCon.getConName());
            received.add(oldCon);
            received.add(newCon);
        }

        @Override
        public void afterDelete(IJDBCInfoChangedSubject initiator, IJDBCConnecInfo delCon) {
            events.add("delete " + delCon.getConName());
            received.add(delCon);
        }
    }

    private static final class FailingObserver implements IJDBCConnectionChangedObserver {
        @Override
        public void afterAdd(IJDBCInfoChangedSubject initiator, IJDBCConnecInfo newCon) {
            throw new IllegalStateException("observer failed");
        }

        @Override
        public void afterModify(
                IJDBCInfoChangedSubject initiator, IJDBCConnecInfo oldCon, IJDBCConnecInfo newCon) {
            throw new IllegalStateException("observer failed");
        }

        @Override
        public void afterDelete(IJDBCInfoChangedSubject initiator, IJDBCConnecInfo delCon) {
            throw new IllegalStateException("observer failed");
        }
    }

    @Nested
    @DisplayName("loadFromFile()")
    class LoadFromFile {

        @Test
        @DisplayName("the saved connections are loaded in order with their passwords decrypted")
        void savedConnections_areLoaded(@TempDir Path dir) throws Exception {
            Path file =
                    file(
                            dir,
                            "<database charSet=\"latin1\" databaseTypeID=\"0\""
                                + " dbName=\"migtestforhudson\""
                                + " driverPath=\"mysql-connector-java-5.1.22-bin.jar\""
                                + " encrypted=\"true\" hostIP=\"localhost\" isXMLDatabase=\"false\""
                                + " name=\"mysqltest\" password=\""
                                    + PASSWORD
                                    + "\" port=\"3306\" user=\"cmt\""
                                    + " user_jdbc_url=\"\"/>",
                            "<database charSet=\"AL32UTF8\" databaseTypeID=\"3\" dbName=\"XE\""
                                    + " driverPath=\"ojdbc14.jar\" encrypted=\"true\""
                                    + " hostIP=\"localhost\" isXMLDatabase=\"false\""
                                    + " name=\"oracletest\" password=\""
                                    + PASSWORD
                                    + "\" port=\"1521\" user=\"migtestforhudson\""
                                    + " user_jdbc_url=\"jdbc:oracle:thin:@localhost:1521/XE\"/>",
                            "<database charSet=\"UTF-8\" databaseTypeID=\"1\""
                                + " dbName=\"migtestforhudson\" driverPath=\"JDBC-8.4.3.1005.jar\""
                                + " encrypted=\"true\" hostIP=\"localhost\" isXMLDatabase=\"false\""
                                + " name=\"migtestforhudson\" password=\""
                                    + PASSWORD
                                    + "\" port=\"30000\" user=\"dba\"/>",
                            "<database charSet=\"UTF-8\" databaseTypeID=\"1\" dbName=\"mt_cubrid\""
                                + " driverPath=\"JDBC-8.4.3.1005.jar\" encrypted=\"true\""
                                + " hostIP=\"localhost\" isXMLDatabase=\"false\" name=\"mt_cubrid\""
                                + " password=\""
                                    + PASSWORD
                                    + "\" port=\"30000\" user=\"dba\"/>");

            manager.loadFromFile(file.toFile());

            assertThat(names())
                    .containsExactly("mysqltest", "oracletest", "migtestforhudson", "mt_cubrid");
            ConnParameters mysql = manager.getConnection("mysqltest");
            assertThat(mysql.getDatabaseType()).isSameAs(DatabaseType.MYSQL);
            assertThat(mysql.getHost()).isEqualTo("localhost");
            assertThat(mysql.getPort()).isEqualTo(3306);
            assertThat(mysql.getDbName()).isEqualTo("migtestforhudson");
            assertThat(mysql.getConUser()).isEqualTo("cmt");
            assertThat(mysql.getConPassword()).isEqualTo("pw");
            assertThat(mysql.getCharset()).isEqualTo("latin1");
            assertThat(mysql.getDriverFileName()).isEqualTo("mysql-connector-java-5.1.22-bin.jar");
            assertThat(mysql.getUserJDBCURL()).isEmpty();
            assertThat(manager.getConnection("oracletest").getUserJDBCURL())
                    .isEqualTo("jdbc:oracle:thin:@localhost:1521/XE");
        }

        @Test
        @DisplayName("an XML database entry is skipped")
        void xmlDatabase_isSkipped(@TempDir Path dir) throws Exception {
            Path file =
                    file(
                            dir,
                            "<database databaseTypeID=\"1\" dbName=\"x\" hostIP=\"h\""
                                + " isXMLDatabase=\"true\" name=\"xml\" port=\"1\" user=\"u\"/>");

            manager.loadFromFile(file.toFile());

            assertThat(names()).isEmpty();
        }

        @Test
        @DisplayName("a password not marked encrypted is taken as it is")
        void plainPassword_isTakenAsItIs(@TempDir Path dir) throws Exception {
            Path file =
                    file(
                            dir,
                            "<database databaseTypeID=\"1\" dbName=\"d\" hostIP=\"h\" name=\"c\""
                                    + " password=\"plain\" port=\"30000\" user=\"dba\"/>");

            manager.loadFromFile(file.toFile());

            assertThat(manager.getConnection("c").getConPassword()).isEqualTo("plain");
        }

        @Test
        @DisplayName("the Informix server name is read")
        void informixServer_isRead(@TempDir Path dir) throws Exception {
            Path file =
                    file(
                            dir,
                            "<database con_server=\"ol_server\" databaseTypeID=\"5\" dbName=\"d\""
                                    + " hostIP=\"h\" name=\"ifx\" port=\"9088\" user=\"u\"/>");

            manager.loadFromFile(file.toFile());

            assertThat(manager.getConnection("ifx").getConServer()).isEqualTo("ol_server");
        }

        @Test
        @DisplayName("loading tells no observer and keeps the first of two entries with one name")
        void loading_isSilentAndKeepsTheFirstOfAName(@TempDir Path dir) throws Exception {
            Path file =
                    file(
                            dir,
                            "<database databaseTypeID=\"0\" dbName=\"d1\" hostIP=\"h\""
                                    + " name=\"same\" port=\"1\" user=\"u\"/>",
                            "<database databaseTypeID=\"0\" dbName=\"d2\" hostIP=\"h\""
                                    + " name=\"same\" port=\"1\" user=\"u\"/>");
            manager.addObservor(recorder);

            manager.loadFromFile(file.toFile());

            assertThat(manager.getConnection("same").getDbName()).isEqualTo("d1");
            assertThat(recorder.events).isEmpty();
        }

        @Test
        @DisplayName("a missing or unreadable file loads nothing")
        void missingOrUnreadableFile_loadsNothing(@TempDir Path dir) throws Exception {
            Path broken =
                    Files.write(
                            dir.resolve("broken.xml"),
                            "<databases>".getBytes(StandardCharsets.UTF_8));

            manager.loadFromFile(dir.resolve("none.xml").toFile());
            manager.loadFromFile(broken.toFile());

            assertThat(names()).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("an entry with a bad port or type stops the load with an exception")
        @MethodSource(
                "com.cubrid.cubridmigration.core.connection.CMTConParamManagerTest#badEntries")
        void badEntry_stopsTheLoad(
                String label,
                String attributes,
                Class<? extends Throwable> expected,
                @TempDir Path dir)
                throws Exception {
            // DEFECT: only file and IO errors are caught, so a bad port or type ends the load with
            // an exception and the entries after it are never read
            // - see CMTConParamManager.loadFromFile()
            Path file =
                    file(
                            dir,
                            "<database databaseTypeID=\"0\" dbName=\"d1\" hostIP=\"h\""
                                    + " name=\"first\" port=\"1\" user=\"u\"/>",
                            "<database "
                                    + attributes
                                    + " dbName=\"d2\" hostIP=\"h\" name=\"broken\""
                                    + " user=\"u\"/>",
                            "<database databaseTypeID=\"0\" dbName=\"d3\" hostIP=\"h\""
                                    + " name=\"third\" port=\"3\" user=\"u\"/>");

            assertThatThrownBy(() -> manager.loadFromFile(file.toFile()))
                    .isExactlyInstanceOf(expected);
            assertThat(names()).containsExactly("first");
        }

        @Test
        @DisplayName("loading the default file after a bad entry leaves only the entries read")
        void loadingTheDefaultFile_dropsTheEntriesNotRead(@TempDir Path dir) throws Exception {
            // DEFECT: each silent addConnection() still rewrites the default file, which callers
            // load from, so after a bad entry the entries not read yet are gone from the file
            // - see CMTConParamManager.loadFromFile()
            Path file =
                    file(
                            dir,
                            "<database databaseTypeID=\"0\" dbName=\"d1\" hostIP=\"h\""
                                    + " name=\"first\" port=\"1\" user=\"u\"/>",
                            "<database databaseTypeID=\"0\" dbName=\"d2\" hostIP=\"h\""
                                    + " name=\"broken\" port=\"x\" user=\"u\"/>",
                            "<database databaseTypeID=\"0\" dbName=\"d3\" hostIP=\"h\""
                                    + " name=\"third\" port=\"3\" user=\"u\"/>");
            manager.setDefaultFile(file.toFile());

            assertThatThrownBy(() -> manager.loadFromFile(file.toFile()))
                    .isInstanceOf(NumberFormatException.class);
            assertThat(saved(file.toFile()))
                    .extracting(entry -> entry.getString("name"))
                    .containsExactly("first");
        }
    }

    @Nested
    @DisplayName("save2File()")
    class Save2File {

        @Test
        @DisplayName("each connection is written with its password encrypted")
        void connections_areWrittenWithEncryptedPasswords(@TempDir Path dir) throws Exception {
            File file = dir.resolve("connections.xml").toFile();
            manager.setDefaultFile(file);

            manager.addConnection(
                    ConnParameters.getConParam(
                            "mysqltest",
                            "localhost",
                            3306,
                            "migtestforhudson",
                            DatabaseType.MYSQL,
                            "latin1",
                            "cmt",
                            "pw",
                            "mysql-connector-java-5.1.22-bin.jar",
                            null),
                    true);

            IXMLMemento entry = saved(file)[0];
            assertThat(entry.getBoolean("isXMLDatabase")).isFalse();
            assertThat(entry.getString("name")).isEqualTo("mysqltest");
            assertThat(entry.getString("dbName")).isEqualTo("migtestforhudson");
            assertThat(entry.getInteger("databaseTypeID")).isZero();
            assertThat(entry.getString("charSet")).isEqualTo("latin1");
            assertThat(entry.getString("user")).isEqualTo("cmt");
            assertThat(entry.getString("password")).isNotEqualTo("pw");
            assertThat(CipherUtils.decrypt(entry.getString("password"))).isEqualTo("pw");
            assertThat(entry.getBoolean("encrypted")).isTrue();
            assertThat(entry.getString("hostIP")).isEqualTo("localhost");
            assertThat(entry.getString("port")).isEqualTo("3306");
            assertThat(entry.getString("driverPath"))
                    .isEqualTo("mysql-connector-java-5.1.22-bin.jar");
        }

        @Test
        @DisplayName("an unset JDBC URL is left out, and the server name is kept for Informix only")
        void unsetValues_areLeftOut(@TempDir Path dir) throws Exception {
            File file = dir.resolve("connections.xml").toFile();
            manager.setDefaultFile(file);
            ConnParameters informix =
                    ConnParameters.getConParam(
                            "ifx",
                            "h",
                            9088,
                            "d",
                            DatabaseType.INFORMIX,
                            "UTF-8",
                            "u",
                            "pw",
                            null,
                            null);
            informix.setConServer("ol_server");
            ConnParameters cubrid = connection("cub", 30000);
            cubrid.setConServer("ignored");

            manager.addConnection(informix, true);
            manager.addConnection(cubrid, true);

            IXMLMemento[] entries = saved(file);
            assertThat(entries[0].getString("con_server")).isEqualTo("ol_server");
            assertThat(entries[1].getString("con_server")).isNull();
            assertThat(entries[1].getString("user_jdbc_url")).isNull();
        }

        @Test
        @DisplayName("the saved file loads back into the same connections")
        void savedFile_loadsBack(@TempDir Path dir) throws Exception {
            File file = dir.resolve("connections.xml").toFile();
            manager.setDefaultFile(file);
            manager.addConnection(connection("a", 30000), true);
            CMTConParamManager other = newManagerFrom(file);

            ConnParameters loaded = other.getConnection("a");
            assertThat(loaded.isSameDB(connection("a", 30000))).isTrue();
            assertThat(loaded.getConPassword()).isEqualTo("pw");
        }

        @Test
        @DisplayName("without a default file nothing is written")
        void noDefaultFile_writesNothing(@TempDir Path dir) throws Exception {
            manager.addConnection(connection("a", 30000), true);

            manager.save2File();

            assertThat(dir).isEmptyDirectory();
            assertThat(names()).containsExactly("a");
        }

        @Test
        @DisplayName("a password of 32 bytes makes the add fail after the connection is stored")
        void passwordOf32Bytes_failsTheAdd(@TempDir Path dir) {
            // DEFECT: CipherUtils.encrypt() fails for a password of a multiple of 32 bytes, and
            // only parser and IO errors are caught, so the caller gets the exception while the
            // connection is already in the list and the file is not written
            // - see CMTConParamManager.save2File()
            File file = dir.resolve("connections.xml").toFile();
            manager.setDefaultFile(file);
            ConnParameters cp = connection("a", 30000);
            cp.setConPassword("0123456789abcdef0123456789abcdef");

            assertThatThrownBy(() -> manager.addConnection(cp, true))
                    .isInstanceOf(ArrayIndexOutOfBoundsException.class);
            assertThat(names()).containsExactly("a");
            assertThat(file).doesNotExist();
        }

        private CMTConParamManager newManagerFrom(File file) throws Exception {
            Constructor<CMTConParamManager> constructor =
                    CMTConParamManager.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            CMTConParamManager other = constructor.newInstance();
            other.loadFromFile(file);
            return other;
        }
    }

    @Nested
    @DisplayName("addConnection()")
    class AddConnection {

        @Test
        @DisplayName("a copy of the connection is stored and saved")
        void copy_isStoredAndSaved(@TempDir Path dir) throws Exception {
            File file = dir.resolve("connections.xml").toFile();
            manager.setDefaultFile(file);
            ConnParameters cp = connection("a", 30000);

            manager.addConnection(cp, true);
            cp.setHost("changed");

            assertThat(manager.getConnection("a").getHost()).isEqualTo("localhost");
            assertThat(saved(file))
                    .extracting(entry -> entry.getString("name"))
                    .containsExactly("a");
        }

        @Test
        @DisplayName("null, a name in use or a database already saved is ignored")
        void nullUsedNameOrSavedDatabase_isIgnored() {
            manager.addConnection(connection("a", 30000), true);
            ConnParameters sameDatabase = connection("b", 30000);
            sameDatabase.setConUser("DBA");

            manager.addConnection(null, true);
            manager.addConnection(connection("a", 30001), true);
            manager.addConnection(sameDatabase, true);
            manager.addConnection(connection("A", 30002), true);

            assertThat(names()).containsExactly("a", "A");
        }

        @Test
        @DisplayName("the observers are told unless the add is silent")
        void observers_areToldUnlessSilent() {
            manager.addObservor(recorder);

            manager.addConnection(connection("a", 30000), false);
            manager.addConnection(connection("b", 30001), true);

            assertThat(recorder.events).containsExactly("add a");
        }

        @Test
        @DisplayName("a failing observer does not stop the others")
        void failingObserver_doesNotStopTheOthers() {
            manager.addObservor(new FailingObserver());
            manager.addObservor(recorder);

            manager.addConnection(connection("a", 30000), false);

            assertThat(recorder.events).containsExactly("add a");
        }

        @Test
        @DisplayName("the observers get the caller's own object, not the stored copy")
        void observers_getTheCallersObject() {
            // DEFECT: the caller's cp is handed to the observers instead of the stored copy, so
            // the shared observer list keeps an object the caller can still change; update and
            // remove hand out snapshots
            // - see CMTConParamManager.addConnection()
            manager.addObservor(recorder);
            ConnParameters cp = connection("a", 30000);

            manager.addConnection(cp, false);

            assertThat(recorder.received).singleElement().isSameAs(cp);
        }
    }

    @Nested
    @DisplayName("updateConnection()")
    class UpdateConnection {

        @Test
        @DisplayName("the same database under a new name takes the new values and keeps its caches")
        void sameDatabase_keepsItsCaches() {
            manager.addConnection(connection("a", 30000), true);
            manager.addConnection(connection("b", 30001), true);
            ConnParameters a = manager.getConnection("a");
            Catalog catalog = new Catalog();
            SchemaCatalog schemaCatalog =
                    new SchemaCatalog("s", DatabaseType.CUBRID, a, null, null);
            manager.updateCatalog("a", catalog);
            manager.updateSelectedSourceCatalog(a, Collections.singletonList("s1"), catalog);
            manager.updateSourceSchemaCatalog(a, schemaCatalog);
            ConnParameters renamed = a.clone();
            renamed.setName("a2");
            renamed.setConPassword("newpw");

            manager.updateConnection("a", renamed, true);

            ConnParameters stored = manager.getConnection("a2");
            assertThat(names()).containsExactly("a2", "b");
            assertThat(stored.getConPassword()).isEqualTo("newpw");
            assertThat(manager.getCatalog("a2")).isSameAs(catalog);
            assertThat(manager.getSelectedSourceCatalog(stored, Collections.singletonList("s1")))
                    .isSameAs(catalog);
            assertThat(manager.getSourceSchemaCatalog(stored)).isSameAs(schemaCatalog);
        }

        @Test
        @DisplayName("a move to another database drops the caches")
        void otherDatabase_dropsTheCaches() {
            manager.addConnection(connection("a", 30000), true);
            ConnParameters a = manager.getConnection("a");
            Catalog catalog = new Catalog();
            manager.updateCatalog("a", catalog);
            manager.updateSelectedSourceCatalog(a, Collections.singletonList("s1"), catalog);
            manager.updateSourceSchemaCatalog(
                    a, new SchemaCatalog("s", DatabaseType.CUBRID, a, null, null));
            ConnParameters moved = connection("a", 30000);
            moved.setHost("other");

            manager.updateConnection("a", moved, true);

            ConnParameters stored = manager.getConnection("a");
            assertThat(stored.getHost()).isEqualTo("other");
            assertThat(manager.getCatalog("a")).isNull();
            assertThat(manager.getSelectedSourceCatalog(stored, Collections.singletonList("s1")))
                    .isNull();
            assertThat(manager.getSelectedSourceCatalog(a, Collections.singletonList("s1")))
                    .isNull();
            assertThat(manager.getSourceSchemaCatalog(stored)).isNull();
            assertThat(manager.getSourceSchemaCatalog(a)).isNull();
        }

        @Test
        @DisplayName("the connection keeps its place in the list")
        void connection_keepsItsPlace() {
            manager.addConnection(connection("a", 30000), true);
            manager.addConnection(connection("b", 30001), true);
            manager.addConnection(connection("c", 30002), true);
            ConnParameters b = manager.getConnection("b");
            b.setName("b2");

            manager.updateConnection("b", b, true);
            manager.updateConnection("c", connection("c2", 30009), true);

            assertThat(names()).containsExactly("a", "b2", "c2");
        }

        @Test
        @DisplayName("an unknown old name or a null connection is ignored")
        void unknownNameOrNullConnection_isIgnored() {
            manager.addObservor(recorder);
            manager.addConnection(connection("a", 30000), true);

            manager.updateConnection("zzz", connection("b", 30001), false);
            manager.updateConnection("a", null, false);

            assertThat(names()).containsExactly("a");
            assertThat(recorder.events).isEmpty();
        }

        @Test
        @DisplayName("a move onto another saved database is ignored")
        void moveOntoAnotherSavedDatabase_isIgnored() {
            manager.addConnection(connection("a", 30000), true);
            manager.addConnection(connection("b", 30001), true);

            manager.updateConnection("a", connection("a", 30001), false);

            assertThat(manager.getConnection("a").getPort()).isEqualTo(30000);
        }

        @Test
        @DisplayName("the observers get copies of the old and new connection unless silent")
        void observers_getCopiesUnlessSilent() {
            manager.addObservor(recorder);
            manager.addConnection(connection("a", 30000), true);
            ConnParameters renamed = manager.getConnection("a");
            renamed.setName("a2");

            manager.updateConnection("a", renamed, false);
            ConnParameters again = manager.getConnection("a2");
            again.setName("a3");
            manager.updateConnection("a2", again, true);

            assertThat(recorder.events).containsExactly("modify a -> a2");
            assertThat(recorder.received.get(1)).isNotSameAs(renamed);
        }

        @Test
        @DisplayName("a failing observer does not stop the others")
        void failingObserver_doesNotStopTheOthers() {
            manager.addObservor(new FailingObserver());
            manager.addObservor(recorder);
            manager.addConnection(connection("a", 30000), true);
            ConnParameters renamed = manager.getConnection("a");
            renamed.setName("a2");

            manager.updateConnection("a", renamed, false);

            assertThat(recorder.events).containsExactly("modify a -> a2");
        }

        @Test
        @DisplayName("the change is saved")
        void change_isSaved(@TempDir Path dir) throws Exception {
            File file = dir.resolve("connections.xml").toFile();
            manager.addConnection(connection("a", 30000), true);
            manager.setDefaultFile(file);
            ConnParameters renamed = manager.getConnection("a");
            renamed.setName("a2");

            manager.updateConnection("a", renamed, true);

            assertThat(saved(file))
                    .extracting(entry -> entry.getString("name"))
                    .containsExactly("a2");
        }

        @Test
        @DisplayName("a new name taken by another connection is accepted")
        void nameOfAnotherConnection_isAccepted() {
            // DEFECT: unlike addConnection(), the new name is never checked against the other
            // connections, so two connections end up with the same name
            // - see CMTConParamManager.updateConnection()
            manager.addConnection(connection("a", 30000), true);
            manager.addConnection(connection("b", 30001), true);
            ConnParameters b = manager.getConnection("b");
            b.setName("a");

            manager.updateConnection("b", b, true);

            assertThat(names()).containsExactly("a", "a");
        }
    }

    @Nested
    @DisplayName("updateCatalog()")
    class UpdateCatalog {

        @Test
        @DisplayName("a catalog is kept under the connection name, and null clears it")
        void catalog_isKeptUnderTheName() {
            manager.addConnection(connection("a", 30000), true);
            Catalog catalog = new Catalog();

            manager.updateCatalog("a", catalog);
            Catalog cached = manager.getCatalog("a");
            manager.updateCatalog("a", null);

            assertThat(cached).isSameAs(catalog);
            assertThat(manager.getCatalog("a")).isNull();
        }

        @Test
        @DisplayName("an unknown or differently cased name is ignored")
        void unknownOrDifferentlyCasedName_isIgnored() {
            manager.addConnection(connection("a", 30000), true);

            manager.updateCatalog("A", new Catalog());
            manager.updateCatalog("missing", new Catalog());

            assertThat(manager.getCatalog("a")).isNull();
            assertThat(manager.getCatalog("missing")).isNull();
        }
    }

    @Nested
    @DisplayName("updateSelectedSourceCatalog()")
    class UpdateSelectedSourceCatalog {

        @Test
        @DisplayName("the catalog is kept for the trimmed schema set, in any order")
        void catalog_isKeptForTheTrimmedSchemaSet() {
            ConnParameters cp = connection("a", 30000);
            Catalog catalog = new Catalog();

            manager.updateSelectedSourceCatalog(cp, Arrays.asList(" s1 ", "s2"), catalog);

            assertThat(manager.getSelectedSourceCatalog(cp, Arrays.asList("s2", "s1")))
                    .isSameAs(catalog);
            assertThat(manager.getSelectedSourceCatalog(cp, Collections.singletonList("s1")))
                    .isNull();
        }

        @Test
        @DisplayName("a missing connection, schema list or catalog is ignored")
        void missingInput_isIgnored() {
            ConnParameters cp = connection("a", 30000);
            List<String> schemas = Collections.singletonList("s");

            manager.updateSelectedSourceCatalog(null, schemas, new Catalog());
            manager.updateSelectedSourceCatalog(cp, null, new Catalog());
            manager.updateSelectedSourceCatalog(cp, Collections.<String>emptyList(), new Catalog());
            manager.updateSelectedSourceCatalog(cp, schemas, null);

            assertThat(manager.getSelectedSourceCatalog(cp, schemas)).isNull();
            assertThat(manager.getSelectedSourceCatalog(cp, Collections.<String>emptyList()))
                    .isNull();
        }

        @Test
        @DisplayName("a list of blank names is kept as the empty selection")
        void blankNames_areKeptAsTheEmptySelection() {
            // DEFECT: the empty check runs before the names are trimmed, so a list of blank or
            // null names passes, becomes the empty selection, and a lookup with no schemas finds it
            // - see CMTConParamManager.updateSelectedSourceCatalog()
            ConnParameters cp = connection("a", 30000);
            Catalog catalog = new Catalog();

            manager.updateSelectedSourceCatalog(cp, Arrays.asList("  ", null), catalog);

            assertThat(manager.getSelectedSourceCatalog(cp, Collections.<String>emptyList()))
                    .isSameAs(catalog);
            assertThat(manager.getSelectedSourceCatalog(cp, null)).isSameAs(catalog);
        }
    }

    @Nested
    @DisplayName("getCatalog()")
    class GetCatalog {

        @Test
        @DisplayName("an unknown name gives null")
        void unknownName_returnsNull() {
            assertThat(manager.getCatalog("missing")).isNull();
        }

        @Test
        @DisplayName("a connection without a cached catalog gives null")
        void noCachedCatalog_returnsNull() {
            manager.addConnection(connection("a", 30000), true);

            assertThat(manager.getCatalog("a")).isNull();
        }
    }

    @Nested
    @DisplayName("getSelectedSourceCatalog()")
    class GetSelectedSourceCatalog {

        @Test
        @DisplayName("any connection to the same database finds the catalog")
        void sameDatabase_findsTheCatalog() {
            Catalog catalog = new Catalog();
            manager.updateSelectedSourceCatalog(
                    connection("a", 30000), Collections.singletonList("s1"), catalog);
            ConnParameters other = connection("other", 30000);
            other.setConUser("DBA");

            assertThat(manager.getSelectedSourceCatalog(other, Collections.singletonList("s1")))
                    .isSameAs(catalog);
        }

        @Test
        @DisplayName("a null connection gives null")
        void nullConnection_returnsNull() {
            assertThat(manager.getSelectedSourceCatalog(null, Collections.singletonList("s1")))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("getConnections()")
    class GetConnections {

        @Test
        @DisplayName("changing the returned list or its connections changes nothing stored")
        void returnedList_holdsCopies() {
            manager.addConnection(connection("a", 30000), true);

            List<ConnParameters> connections = manager.getConnections();
            connections.get(0).setHost("changed");
            connections.clear();

            assertThat(names()).containsExactly("a");
            assertThat(manager.getConnection("a").getHost()).isEqualTo("localhost");
        }
    }

    @Nested
    @DisplayName("getConnection()")
    class GetConnection {

        @Test
        @DisplayName("the name must match exactly, case included")
        void name_mustMatchExactly() {
            manager.addConnection(connection("a", 30000), true);

            assertThat(manager.getConnection("a")).isNotNull();
            assertThat(manager.getConnection("A")).isNull();
            assertThat(manager.getConnection("missing")).isNull();
        }

        @Test
        @DisplayName("each call gives a fresh copy")
        void eachCall_givesAFreshCopy() {
            manager.addConnection(connection("a", 30000), true);

            ConnParameters copy = manager.getConnection("a");
            copy.setHost("changed");

            assertThat(manager.getConnection("a")).isNotSameAs(copy);
            assertThat(manager.getConnection("a").getHost()).isEqualTo("localhost");
        }
    }

    @Nested
    @DisplayName("removeConnection()")
    class RemoveConnection {

        @Test
        @DisplayName("the connection and its caches are removed and the file is saved")
        void connectionAndCaches_areRemoved(@TempDir Path dir) throws Exception {
            File file = dir.resolve("connections.xml").toFile();
            manager.addConnection(connection("a", 30000), true);
            manager.addConnection(connection("b", 30001), true);
            ConnParameters a = manager.getConnection("a");
            Catalog catalog = new Catalog();
            manager.updateCatalog("a", catalog);
            manager.updateSelectedSourceCatalog(a, Collections.singletonList("s1"), catalog);
            manager.updateSourceSchemaCatalog(
                    a, new SchemaCatalog("s", DatabaseType.CUBRID, a, null, null));
            manager.setDefaultFile(file);

            manager.removeConnection("a", true);

            assertThat(names()).containsExactly("b");
            assertThat(manager.getCatalog("a")).isNull();
            assertThat(manager.getSelectedSourceCatalog(a, Collections.singletonList("s1")))
                    .isNull();
            assertThat(manager.getSourceSchemaCatalog(a)).isNull();
            assertThat(saved(file))
                    .extracting(entry -> entry.getString("name"))
                    .containsExactly("b");
        }

        @Test
        @DisplayName("the same database added again starts without the old catalog")
        void sameDatabaseAddedAgain_startsWithoutTheOldCatalog() {
            manager.addConnection(connection("a", 30000), true);
            manager.updateCatalog("a", new Catalog());

            manager.removeConnection("a", true);
            manager.addConnection(connection("a", 30000), true);

            assertThat(manager.getCatalog("a")).isNull();
        }

        @Test
        @DisplayName("the observers get a copy of the removed connection unless silent")
        void observers_getACopyUnlessSilent() {
            manager.addObservor(recorder);
            manager.addConnection(connection("a", 30000), true);
            manager.addConnection(connection("b", 30001), true);

            manager.removeConnection("a", false);
            manager.removeConnection("b", true);

            assertThat(recorder.events).containsExactly("delete a");
            assertThat(recorder.received.get(0).getConName()).isEqualTo("a");
        }

        @Test
        @DisplayName("a failing observer does not stop the others")
        void failingObserver_doesNotStopTheOthers() {
            manager.addObservor(new FailingObserver());
            manager.addObservor(recorder);
            manager.addConnection(connection("a", 30000), true);

            manager.removeConnection("a", false);

            assertThat(recorder.events).containsExactly("delete a");
        }

        @Test
        @DisplayName("an unknown or differently cased name is ignored")
        void unknownOrDifferentlyCasedName_isIgnored() {
            manager.addObservor(recorder);
            manager.addConnection(connection("a", 30000), true);

            manager.removeConnection("A", false);
            manager.removeConnection("missing", false);

            assertThat(names()).containsExactly("a");
            assertThat(recorder.events).isEmpty();
        }
    }

    @Nested
    @DisplayName("isNameUsed()")
    class IsNameUsed {

        @Test
        @DisplayName("names compare with case, so test and Test differ")
        void names_compareWithCase() {
            manager.addConnection(connection("test", 30000), true);

            assertThat(manager.isNameUsed("test")).isTrue();
            assertThat(manager.isNameUsed("Test")).isFalse();
        }

        @Test
        @DisplayName("null is never in use")
        void nullName_isNotInUse() {
            manager.addConnection(connection("test", 30000), true);

            assertThat(manager.isNameUsed(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isConnectionExists()")
    class IsConnectionExists {

        @Test
        @DisplayName("a connection to a saved database exists whatever its name")
        void savedDatabase_existsUnderAnyName() {
            manager.addConnection(connection("a", 30000), true);
            ConnParameters other = connection("other", 30000);
            other.setConUser("DBA");
            other.setDbName("DEMODB");

            assertThat(manager.isConnectionExists(other)).isTrue();
        }

        @Test
        @DisplayName("another port or null does not exist")
        void otherPortOrNull_doesNotExist() {
            manager.addConnection(connection("a", 30000), true);

            assertThat(manager.isConnectionExists(connection("a", 30001))).isFalse();
            assertThat(manager.isConnectionExists(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("addObservor()")
    class AddObservor {

        @Test
        @DisplayName("an observer added twice is told once")
        void sameObserverTwice_isToldOnce() {
            manager.addObservor(recorder);
            manager.addObservor(recorder);

            manager.addConnection(connection("a", 30000), false);

            assertThat(recorder.events).containsExactly("add a");
        }
    }

    static Stream<Arguments> badEntries() {
        return Stream.of(
                Arguments.of(
                        "port that is not a number",
                        "databaseTypeID=\"0\" port=\"x\"",
                        NumberFormatException.class),
                Arguments.of("no port", "databaseTypeID=\"0\"", NumberFormatException.class),
                Arguments.of("no type", "port=\"2\"", NullPointerException.class),
                Arguments.of(
                        "unknown type",
                        "databaseTypeID=\"42\" port=\"2\"",
                        RuntimeException.class));
    }
}
