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
package com.cubrid.cubridmigration.core.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.testutil.PathUtilsState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

@DisplayName("PathUtils")
@ResourceLock(Resources.LOCALE)
class PathUtilsTest {

    private static final String SEP = File.separator;
    private static final Locale ORIGINAL_LOCALE = Locale.getDefault();

    private PathUtilsState state;

    @BeforeEach
    void captureState() {
        state = PathUtilsState.capture();
    }

    @AfterEach
    void restoreState() {
        state.restore();
        Locale.setDefault(ORIGINAL_LOCALE);
    }

    private static Path touch(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.write(file, "x".getBytes(StandardCharsets.UTF_8));
    }

    private static void setField(String name, String value) throws Exception {
        Field field = PathUtils.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static String invoke(String name, String path, String root) throws Exception {
        Method method = PathUtils.class.getDeclaredMethod(name, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, path, root);
    }

    @Nested
    @DisplayName("checkPathExist()")
    class CheckPathExist {

        @Test
        @DisplayName("an existing folder passes")
        void existingFolder_returnsTrue(@TempDir Path dir) {
            assertThat(PathUtils.checkPathExist(dir.toFile())).isTrue();
        }

        @Test
        @DisplayName("a missing folder is created with its parents")
        void missingFolder_isCreated(@TempDir Path dir) {
            Path folder = dir.resolve("a/b");

            assertThat(PathUtils.checkPathExist(folder.toFile())).isTrue();
            assertThat(folder).isDirectory();
        }

        @Test
        @DisplayName("a regular file passes as if it were the folder")
        void regularFile_returnsTrue(@TempDir Path dir) throws Exception {
            // DEFECT: only existence is checked, so a regular file with the folder's name passes
            // and the caller goes on to use it as a folder
            // - see PathUtils.checkPathExist()
            Path file = touch(dir.resolve("f.txt"));

            assertThat(PathUtils.checkPathExist(file.toFile())).isTrue();
            assertThat(file).isRegularFile();
        }

        @Test
        @DisplayName("a folder that cannot be created fails")
        void uncreatableFolder_returnsFalse(@TempDir Path dir) throws Exception {
            Path blocker = touch(dir.resolve("blocker"));

            assertThat(PathUtils.checkPathExist(blocker.resolve("sub").toFile())).isFalse();
        }
    }

    @Nested
    @DisplayName("checkPathEmpty()")
    class CheckPathEmpty {

        @Test
        @DisplayName("a folder without entries is empty")
        void folderWithoutEntries_returnsTrue(@TempDir Path dir) {
            assertThat(PathUtils.checkPathEmpty(dir.toFile())).isTrue();
        }

        @Test
        @DisplayName("a folder holding only a subfolder is not empty")
        void folderWithSubfolder_returnsFalse(@TempDir Path dir) throws Exception {
            Files.createDirectory(dir.resolve("sub"));

            assertThat(PathUtils.checkPathEmpty(dir.toFile())).isFalse();
        }

        @Test
        @DisplayName("a file or a missing path is not an empty folder")
        void fileOrMissingPath_returnsFalse(@TempDir Path dir) throws Exception {
            assertThat(PathUtils.checkPathEmpty(touch(dir.resolve("f.txt")).toFile())).isFalse();
            assertThat(PathUtils.checkPathEmpty(dir.resolve("none").toFile())).isFalse();
        }

        @Test
        @DisplayName("a folder that cannot be listed counts as empty")
        void unlistableFolder_returnsTrue(@TempDir Path dir) throws Exception {
            // DEFECT: list() returns null on an I/O error such as a missing read permission, and
            // that is taken for an empty folder even when it holds files
            // - see PathUtils.checkPathEmpty()
            File folder = touch(dir.resolve("locked/f.txt")).getParent().toFile();
            assumeTrue(folder.setReadable(false), "read permission cannot be removed");
            try {
                assumeTrue(folder.list() == null, "the folder can still be listed");

                assertThat(PathUtils.checkPathEmpty(folder)).isTrue();
            } finally {
                folder.setReadable(true);
            }
        }
    }

    @Nested
    @DisplayName("createFile()")
    class CreateFile {

        @Test
        @DisplayName("the file is created together with its missing parent folders")
        void missingParents_areCreated(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("a/b/c.txt");

            PathUtils.createFile(file.toFile());

            assertThat(file).isEmptyFile();
        }

        @Test
        @DisplayName("an existing file is rejected")
        void existingFile_throwsIOException(@TempDir Path dir) throws Exception {
            Path file = touch(dir.resolve("a.txt"));

            assertThatThrownBy(() -> PathUtils.createFile(file.toFile()))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Create file failed:" + file.toAbsolutePath());
        }

        @Test
        @DisplayName("a parent folder that cannot be created is reported by the folder above it")
        void uncreatableParent_reportsFolderAboveIt(@TempDir Path dir) throws Exception {
            // DEFECT: the message takes getParent() of the folder that failed, so it names the
            // folder one level above the one that could not be created
            // - see PathUtils.createFile()
            Path blocker = touch(dir.resolve("blocker"));

            assertThatThrownBy(() -> PathUtils.createFile(blocker.resolve("sub/c.txt").toFile()))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Create dictionary failed:" + blocker);
        }
    }

    @Nested
    @DisplayName("extracFileExt()")
    class ExtracFileExt {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("the text after the last dot is the extension")
        @CsvSource({"test.t.test, test", "a.txt, txt", "a.tar.gz, gz", ".bashrc, bashrc"})
        void textAfterLastDot_isTheExtension(String fileName, String expected) {
            assertThat(PathUtils.extracFileExt(fileName)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"\"")
        @DisplayName("a blank name, a name without a dot or one ending in a dot has no extension")
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "a", "a."})
        void nothingAfterADot_returnsEmptyString(String fileName) {
            assertThat(PathUtils.extracFileExt(fileName)).isEmpty();
        }

        @Test
        @DisplayName("a dot only in a folder name makes the rest of the path the extension")
        void dotInFolderName_returnsRestOfPath() {
            // DEFECT: the last dot is searched in the whole path, so a dot in a folder name turns
            // the path after it into the extension
            // - see PathUtils.extracFileExt()
            assertThat(PathUtils.extracFileExt("d.dir/file")).isEqualTo("dir/file");
        }
    }

    @Nested
    @DisplayName("getFileKBSize()")
    class GetFileKBSize {

        @ParameterizedTest(name = "[{index}] {0} bytes -> \"{1}\"")
        @DisplayName("the size is rounded up to whole kilobytes of 1000 bytes")
        @CsvSource({
            "0, 0 KB",
            "1, 1 KB",
            "999, 1 KB",
            "1000, 1 KB",
            "1001, 2 KB",
            "10000000, '10,000 KB'",
            "10000100, '10,001 KB'"
        })
        void size_isRoundedUpToKilobytes(long size, String expected) {
            Locale.setDefault(Locale.US);

            assertThat(PathUtils.getFileKBSize(size)).isEqualTo(expected);
        }

        @Test
        @DisplayName("the thousands separator follows the default locale")
        void thousandsSeparator_followsDefaultLocale() {
            // DEFECT: NumberFormat.getInstance() takes the default locale, so the same size reads
            // 10,000 KB in one locale and 10.000 KB in another
            // - see PathUtils.getFileKBSize()
            Locale.setDefault(Locale.GERMANY);

            assertThat(PathUtils.getFileKBSize(10000000)).isEqualTo("10.000 KB");
        }

        @Test
        @DisplayName("a negative size is rejected")
        void negativeSize_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> PathUtils.getFileKBSize(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File size can't be nagetive.");
        }
    }

    @Nested
    @DisplayName("getLocalHostFilePath()")
    class GetLocalHostFilePath {

        @Test
        @DisplayName("slashes and backslashes both become the file.separator of this JVM")
        void slashesAndBackslashes_becomeFileSeparator() {
            String separator = System.getProperty("file.separator");

            assertThat(PathUtils.getLocalHostFilePath("a\\b/c"))
                    .isEqualTo("a" + separator + "b" + separator + "c");
        }
    }

    @Nested
    @DisplayName("getURLFilePath()")
    class GetURLFilePath {

        @Test
        @DisplayName("outside Windows the URL path is returned as it is")
        void outsideWindows_returnsUrlPath() throws Exception {
            assumeFalse(CUBRIDIOUtils.IS_OS_WINDOWS, "Windows drops the leading slash");

            assertThat(PathUtils.getURLFilePath(URI.create("file:/tmp/c.txt").toURL()))
                    .isEqualTo("/tmp/c.txt");
        }

        @Test
        @DisplayName("percent escapes are left in the path")
        void percentEscapes_areLeftIn() throws Exception {
            // DEFECT: the URL path is not decoded, so a space in a folder name stays %20 and the
            // path names a folder that does not exist
            // - see PathUtils.getURLFilePath()
            assumeFalse(CUBRIDIOUtils.IS_OS_WINDOWS, "Windows drops the leading slash");

            assertThat(PathUtils.getURLFilePath(URI.create("file:/tmp/a%20b/c.txt").toURL()))
                    .isEqualTo("/tmp/a%20b/c.txt");
        }
    }

    @Nested
    @DisplayName("initPaths()")
    class InitPaths {

        @Test
        @DisplayName("the install, JDBC and temp folders are set and every folder is created")
        void everyFolder_isSetAndCreated(@TempDir Path dir) throws Exception {
            Path install = dir.resolve("install");
            Path tmp = dir.resolve("tmp");
            Path ws = dir.resolve("ws");

            PathUtils.initPaths(install.toString(), tmp.toString(), ws.toString());

            String installPath = install.toFile().getCanonicalPath() + SEP;
            assertThat(PathUtils.getInstallPath()).isEqualTo(installPath);
            assertThat(PathUtils.getJDBCLibDir()).isEqualTo(installPath + "jdbc" + SEP);
            assertThat(PathUtils.getBaseTempDir()).isEqualTo(tmp + SEP);
            assertThat(PathUtils.getWorkspace()).isEqualTo(ws + SEP);
            assertThat(
                            Stream.of(
                                            PathUtils.getJDBCLibDir(),
                                            PathUtils.getCMTWorkspace(),
                                            PathUtils.getLogDir(),
                                            PathUtils.getReportDir(),
                                            PathUtils.getScriptDir(),
                                            PathUtils.getMonitorHistoryDir(),
                                            PathUtils.getSchemaCacheDir(),
                                            PathUtils.getErrorsDir(),
                                            PathUtils.getHandlersDir(),
                                            PathUtils.getBaseTempDir())
                                    .map(File::new))
                    .allSatisfy(folder -> assertThat(folder).isDirectory());
        }

        @Test
        @DisplayName("without a temp path the temp folder goes under the workspace")
        void noTempPath_usesWorkspaceTemp(@TempDir Path dir) throws Exception {
            setField("tempDir", null);
            Path ws = dir.resolve("ws");

            PathUtils.initPaths(dir.resolve("install").toString(), null, ws.toString());

            assertThat(PathUtils.getBaseTempDir()).isEqualTo(ws + SEP + "cmt" + SEP + "temp" + SEP);
            assertThat(new File(PathUtils.getBaseTempDir())).isDirectory();
        }

        @Test
        @DisplayName("without a workspace path the workspace goes under the install folder")
        void noWorkspacePath_usesInstallWorkspace(@TempDir Path dir) throws Exception {
            Path install = dir.resolve("install");

            PathUtils.initPaths(install.toString(), dir.resolve("tmp").toString(), null);

            assertThat(PathUtils.getWorkspace())
                    .isEqualTo(install.toFile().getCanonicalPath() + SEP + "workspace" + SEP);
        }
    }

    @Nested
    @DisplayName("getDefaultKrbConfigFile()")
    class GetDefaultKrbConfigFile {

        @Test
        @DisplayName("outside Windows it is /etc/krb5.conf")
        void outsideWindows_returnsEtcKrb5Conf() {
            assumeFalse(CUBRIDIOUtils.IS_OS_WINDOWS, "Windows uses c:\\Windows\\krb5.ini");

            assertThat(PathUtils.getDefaultKrbConfigFile()).isEqualTo("/etc/krb5.conf");
        }
    }

    @Nested
    @DisplayName("mergePath()")
    class MergePath {

        @ParameterizedTest(name = "[{index}] \"{0}\" + \"{1}\" -> \"{2}\"")
        @DisplayName("one separator joins the parts, the one leading the second part first")
        @CsvSource({
            "/home/cmt/, lob/, /home/cmt/lob/",
            "/home/cmt/, /lob/, /home/cmt/lob/",
            "/home/cmt, lob/, /home/cmt/lob/",
            "/home/cmt, , /home/cmt/",
            "/home/cmt/, , /home/cmt/",
            "'a\\', b, 'a\\b'",
            "a, '\\b', 'a\\b'",
            "a/, '\\b', 'a\\b'",
            "a//, b, a//b"
        })
        void parts_areJoinedByOneSeparator(String path1, String path2, String expected) {
            assertThat(PathUtils.mergePath(path1, path2)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" + \"{1}\" -> \"{2}\"")
        @DisplayName("a missing first part still puts a separator in front")
        @CsvSource({", lob/, /lob/", ", /lob/, /lob/", "'', '', /", ", , /"})
        void missingFirstPart_startsWithSeparator(String path1, String path2, String expected) {
            // DEFECT: the separator is added even with nothing before it, so a relative second
            // part becomes an absolute path from the root
            // - see PathUtils.mergePath()
            assertThat(PathUtils.mergePath(path1, path2)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("setBaseTempDir()")
    class SetBaseTempDir {

        @Test
        @DisplayName("a separator is added when the path has none at the end")
        void pathWithoutSeparator_getsOne() {
            PathUtils.setBaseTempDir("tmp");

            assertThat(PathUtils.getBaseTempDir()).isEqualTo("tmp" + SEP);
        }

        @Test
        @DisplayName("a path ending with a separator is kept as it is")
        void pathWithSeparator_isKept() {
            PathUtils.setBaseTempDir("tmp" + SEP);

            assertThat(PathUtils.getBaseTempDir()).isEqualTo("tmp" + SEP);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("a blank path leaves the temp folder as it was")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        void blankPath_isIgnored(String path) {
            PathUtils.setBaseTempDir("kept" + SEP);

            PathUtils.setBaseTempDir(path);

            assertThat(PathUtils.getBaseTempDir()).isEqualTo("kept" + SEP);
        }
    }

    @Nested
    @DisplayName("setWorkspace()")
    class SetWorkspace {

        @Test
        @DisplayName(
                "the cmt folders go under the workspace and the handlers under the install path")
        void workspace_placesEveryFolder() throws Exception {
            setField("installLocation", "install" + SEP);
            setField("tempDir", null);

            PathUtils.setWorkspace("ws");

            String cmt = "ws" + SEP + "cmt" + SEP;
            assertThat(PathUtils.getWorkspace()).isEqualTo("ws" + SEP);
            assertThat(PathUtils.getCMTWorkspace()).isEqualTo(cmt);
            assertThat(PathUtils.getLogDir()).isEqualTo(cmt + "log" + SEP);
            assertThat(PathUtils.getReportDir()).isEqualTo(cmt + "report" + SEP);
            assertThat(PathUtils.getScriptDir()).isEqualTo(cmt + "script" + SEP);
            assertThat(PathUtils.getMonitorHistoryDir()).isEqualTo(cmt + "history" + SEP);
            assertThat(PathUtils.getSchemaCacheDir()).isEqualTo(cmt + "schemacache" + SEP);
            assertThat(PathUtils.getErrorsDir()).isEqualTo(cmt + "errors" + SEP);
            assertThat(PathUtils.getBaseTempDir()).isEqualTo(cmt + "temp" + SEP);
            assertThat(PathUtils.getHandlersDir()).isEqualTo("install" + SEP + "handlers" + SEP);
        }

        @Test
        @DisplayName("a workspace ending with a separator is kept as it is")
        void workspaceWithSeparator_isKept() {
            PathUtils.setWorkspace("ws" + SEP);

            assertThat(PathUtils.getWorkspace()).isEqualTo("ws" + SEP);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("a blank workspace falls back to the workspace folder of the install path")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        void blankWorkspace_usesInstallWorkspace(String ws) throws Exception {
            setField("installLocation", "install" + SEP);

            PathUtils.setWorkspace(ws);

            assertThat(PathUtils.getWorkspace()).isEqualTo("install" + SEP + "workspace" + SEP);
        }

        @Test
        @DisplayName("a second workspace keeps the temp folder of the first")
        void secondWorkspace_keepsFirstTempFolder() throws Exception {
            // DEFECT: the temp folder is only derived while it is null, so after the workspace
            // changes it still points into the old one
            // - see PathUtils.setWorkspace()
            setField("tempDir", null);
            PathUtils.setWorkspace("ws1");

            PathUtils.setWorkspace("ws2");

            assertThat(PathUtils.getCMTWorkspace()).isEqualTo("ws2" + SEP + "cmt" + SEP);
            assertThat(PathUtils.getBaseTempDir())
                    .isEqualTo("ws1" + SEP + "cmt" + SEP + "temp" + SEP);
        }
    }

    @Nested
    @DisplayName("toCanonicalPath()")
    class ToCanonicalPath {

        @Test
        @DisplayName("dot segments are resolved")
        void dotSegments_areResolved(@TempDir Path dir) throws Exception {
            assertThat(PathUtils.toCanonicalPath(dir.resolve("a/../b").toString()))
                    .isEqualTo(dir.resolve("b").toFile().getCanonicalPath());
        }

        @Test
        @DisplayName("a path the file system rejects gives null")
        void invalidPath_returnsNull() {
            assertThat(PathUtils.toCanonicalPath("a\u0000b")).isNull();
        }
    }

    @Nested
    @DisplayName("transStr2FileName()")
    class TransStr2FileName {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("each run of colons, bars and white space becomes one underscore")
        @CsvSource({"a:b c, a_b_c", "'a::  |b', a_b", "'  x  ', _x_"})
        void separatorRuns_becomeOneUnderscore(String text, String expected) {
            assertThat(PathUtils.transStr2FileName(text)).isEqualTo(expected);
        }

        @Test
        @DisplayName("slashes, backslashes and wildcards are kept")
        void pathAndWildcardCharacters_areKept() {
            // DEFECT: only colons, bars and white space are replaced, so characters no file name
            // can hold, such as slashes, backslashes and wildcards, stay in the result
            // - see PathUtils.transStr2FileName()
            assertThat(PathUtils.transStr2FileName("a/b\\c*d?e")).isEqualTo("a/b\\c*d?e");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("a blank text is rejected")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        void blankText_throwsIllegalArgumentException(String text) {
            assertThatThrownBy(() -> PathUtils.transStr2FileName(text))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File name cant be empty.");
        }
    }

    @Nested
    @DisplayName("getUserHomeDir()")
    class GetUserHomeDir {

        @Test
        @DisplayName("a separator is added to the user home")
        void userHome_getsSeparator() {
            String home = System.getProperty("user.home");
            assumeFalse(home.endsWith(SEP), "user.home already ends with a separator");

            assertThat(PathUtils.getUserHomeDir()).isEqualTo(home + SEP);
        }
    }

    @Nested
    @DisplayName("getFileNameWithoutExtendName()")
    class GetFileNameWithoutExtendName {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("everything before the last dot is kept, the path included")
        @CsvSource({
            "/home/xxx/file.xml, /home/xxx/file",
            "'c:\\home\\xxx\\file.xml', 'c:\\home\\xxx\\file'",
            "file.xml, file",
            "a.b.c, a.b",
            "/.xml, /"
        })
        void textBeforeLastDot_isKept(String fileName, String expected) {
            assertThat(PathUtils.getFileNameWithoutExtendName(fileName)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a name without a dot is returned as it is")
        void nameWithoutDot_isReturnedAsIs() {
            assertThat(PathUtils.getFileNameWithoutExtendName("file")).isEqualTo("file");
        }

        @Test
        @DisplayName("a name starting with its only dot gives an empty string")
        void leadingDot_returnsEmptyString() {
            assertThat(PathUtils.getFileNameWithoutExtendName(".xml")).isEmpty();
        }

        @Test
        @DisplayName("null gives null")
        void nullName_returnsNull() {
            assertThat(PathUtils.getFileNameWithoutExtendName(null)).isNull();
        }

        @Test
        @DisplayName("a dot in a folder name cuts the path there")
        void dotInFolderName_cutsThePath() {
            // DEFECT: the last dot is searched in the whole path, so a file without an extension
            // under a folder with a dot loses the rest of the path
            // - see PathUtils.getFileNameWithoutExtendName()
            assertThat(PathUtils.getFileNameWithoutExtendName("dir.v1/file")).isEqualTo("dir");
        }
    }

    @Nested
    @DisplayName("clearTempDir()")
    class ClearTempDir {

        @Test
        @DisplayName("everything inside the temp folder is removed and the folder kept")
        void contents_areRemovedFolderKept(@TempDir Path dir) throws Exception {
            Path temp = dir.resolve("temp");
            touch(temp.resolve("f.txt"));
            touch(temp.resolve("d/g.txt"));
            PathUtils.setBaseTempDir(temp.toString());

            PathUtils.clearTempDir();

            assertThat(temp).isEmptyDirectory();
        }

        @Test
        @DisplayName("a missing temp folder is ignored")
        void missingFolder_isIgnored(@TempDir Path dir) {
            Path temp = dir.resolve("none");
            PathUtils.setBaseTempDir(temp.toString());

            PathUtils.clearTempDir();

            assertThat(temp).doesNotExist();
        }
    }

    @Nested
    @DisplayName("changeLocalFilePath()")
    class ChangeLocalFilePath {

        private MigrationConfiguration renamed(String oldName, String newName) {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setFileRepositroyPath("/repo/");
            config.setName(oldName);
            config.setName(newName);
            return config;
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("each script path moves from the old name to the new one")
        @MethodSource("com.cubrid.cubridmigration.core.common.PathUtilsTest#scriptPathMaps")
        void scriptPath_movesToNewName(
                String name,
                BiConsumer<MigrationConfiguration, Map<String, String>> setter,
                Function<MigrationConfiguration, Map<String, String>> getter) {
            MigrationConfiguration config = renamed("old", "new");
            setter.accept(config, Collections.singletonMap("S", "/repo/old_" + name + ".sql"));

            PathUtils.changeLocalFilePath(config);

            assertThat(getter.apply(config)).containsOnly(entry("S", "/repo/new_" + name + ".sql"));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("paths grouped under a schema move too, and a schema holding none is skipped")
        @MethodSource("com.cubrid.cubridmigration.core.common.PathUtilsTest#groupedPathMaps")
        void groupedPath_movesAndEmptySchemaIsSkipped(
                String name,
                BiConsumer<MigrationConfiguration, Map<String, Map<String, String>>> setter,
                Function<MigrationConfiguration, Map<String, Map<String, String>>> getter) {
            MigrationConfiguration config = renamed("old", "new");
            Map<String, Map<String, String>> paths = new HashMap<>();
            paths.put("S", Collections.singletonMap("U", "/repo/old_" + name + ".sql"));
            paths.put("EMPTY", null);
            setter.accept(config, paths);

            PathUtils.changeLocalFilePath(config);

            assertThat(getter.apply(config))
                    .containsOnlyKeys("S", "EMPTY")
                    .containsEntry("S", Collections.singletonMap("U", "/repo/new_" + name + ".sql"))
                    .containsEntry("EMPTY", null);
        }

        @Test
        @DisplayName("every table data path in a list moves to the new name")
        void tableDataPaths_moveToNewName() {
            MigrationConfiguration config = renamed("old", "new");
            config.setTargetTableDataFileName(
                    Collections.singletonMap(
                            "S", Arrays.asList("/repo/old_t1.txt", "/repo/old_t2.txt")));

            PathUtils.changeLocalFilePath(config);

            assertThat(config.getTargetTableDataFileName())
                    .containsOnly(
                            entry("S", Arrays.asList("/repo/new_t1.txt", "/repo/new_t2.txt")));
        }

        @Test
        @DisplayName("an old name inside the fixed part of a path is replaced there too")
        void oldNameInFixedPart_isReplacedToo() {
            // DEFECT: every occurrence of the old name in the path is replaced, not only the
            // script name part, so a short name also rewrites the fixed suffix
            // - see PathUtils.changeOldNameToNewName()
            MigrationConfiguration config = renamed("a", "b");
            config.setTargetSchemaFileName(Collections.singletonMap("S", "/repo/a_schema.sql"));

            PathUtils.changeLocalFilePath(config);

            assertThat(config.getTargetSchemaFileName())
                    .containsOnly(entry("S", "/repo/b_schemb.sql"));
        }

        @Test
        @DisplayName("an old name in a folder of a listed path is replaced there too")
        void oldNameInListedFolder_isReplacedToo() {
            // DEFECT: every occurrence of the old name in the relative path is replaced, so a
            // folder that carries the name is renamed along with the script
            // - see PathUtils.changeOldNameToNewName()
            MigrationConfiguration config = renamed("old", "new");
            config.setTargetTableDataFileName(
                    Collections.singletonMap(
                            "S", Collections.singletonList("/repo/old/old_t1.txt")));

            PathUtils.changeLocalFilePath(config);

            assertThat(config.getTargetTableDataFileName())
                    .containsOnly(entry("S", Collections.singletonList("/repo/new/new_t1.txt")));
        }

        @Test
        @DisplayName("an empty old name puts the new name around every character")
        void emptyOldName_insertsNewNameEverywhere() {
            // DEFECT: only a null old name stops the rename, and replacing an empty string puts
            // the new name before and after every character
            // - see PathUtils.changeLocalFilePath()
            MigrationConfiguration config = renamed("", "n");
            config.setTargetSchemaFileName(Collections.singletonMap("S", "/repo/ab.sql"));

            PathUtils.changeLocalFilePath(config);

            assertThat(config.getTargetSchemaFileName())
                    .containsOnly(entry("S", "/repo/nanbn.nsnqnln"));
        }

        @Test
        @DisplayName("a configuration named only once keeps its paths")
        void namedOnce_keepsPaths() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setFileRepositroyPath("/repo/");
            config.setName("only");
            config.setTargetSchemaFileName(Collections.singletonMap("S", "/repo/only.sql"));

            PathUtils.changeLocalFilePath(config);

            assertThat(config.getTargetSchemaFileName()).containsOnly(entry("S", "/repo/only.sql"));
        }
    }

    @Nested
    @DisplayName("addRootPath()")
    class AddRootPath {

        @Test
        @DisplayName("a relative path gets the root in front")
        void relativePath_getsRoot() throws Exception {
            assertThat(invoke("addRootPath", "x/a.sql", "/root/")).isEqualTo("/root/x/a.sql");
        }

        @Test
        @DisplayName("a path already under the root is kept")
        void pathUnderRoot_isKept() throws Exception {
            assertThat(invoke("addRootPath", "/root/x", "/root/")).isEqualTo("/root/x");
        }

        @Test
        @DisplayName("an absolute path outside the root still gets the root in front")
        void absolutePathOutsideRoot_getsRootToo() throws Exception {
            // DEFECT: any path not starting with the root gets it in front, so an absolute path
            // elsewhere turns into a path under the root with a doubled separator
            // - see PathUtils.addRootPath()
            assertThat(invoke("addRootPath", "/other/a", "/root/")).isEqualTo("/root//other/a");
        }
    }

    @Nested
    @DisplayName("removeRootPath()")
    class RemoveRootPath {

        @Test
        @DisplayName("the root is cut from a path under it")
        void pathUnderRoot_losesRoot() throws Exception {
            assertThat(invoke("removeRootPath", "/root/x", "/root/")).isEqualTo("x");
        }

        @Test
        @DisplayName("a path outside the root is kept")
        void pathOutsideRoot_isKept() throws Exception {
            assertThat(invoke("removeRootPath", "/other/x", "/root/")).isEqualTo("/other/x");
        }

        @Test
        @DisplayName("a root matching only the start of a folder name is cut too")
        void rootMatchingPartOfFolderName_isCutToo() throws Exception {
            // DEFECT: startsWith does not check for a folder boundary, so the root /out is also
            // cut from /output and leaves part of the folder name behind
            // - see PathUtils.removeRootPath()
            assertThat(invoke("removeRootPath", "/output/a", "/out")).isEqualTo("put/a");
        }
    }

    static Stream<Arguments> scriptPathMaps() {
        return Stream.of(
                pathMap(
                        "schema",
                        MigrationConfiguration::setTargetSchemaFileName,
                        MigrationConfiguration::getTargetSchemaFileName),
                pathMap(
                        "class",
                        MigrationConfiguration::setTargetTableFileName,
                        MigrationConfiguration::getTargetTableFileName),
                pathMap(
                        "vclass",
                        MigrationConfiguration::setTargetViewFileName,
                        MigrationConfiguration::getTargetViewFileName),
                pathMap(
                        "vclass_query_spec",
                        MigrationConfiguration::setTargetViewQuerySpecFileName,
                        MigrationConfiguration::getTargetViewQuerySpecFileName),
                pathMap(
                        "objects",
                        MigrationConfiguration::setTargetDataFileName,
                        MigrationConfiguration::getTargetDataFileName),
                pathMap(
                        "index",
                        MigrationConfiguration::setTargetIndexFileName,
                        MigrationConfiguration::getTargetIndexFileName),
                pathMap(
                        "unique_index",
                        MigrationConfiguration::setTargetUniqueIndexFileName,
                        MigrationConfiguration::getTargetUniqueIndexFileName),
                pathMap(
                        "pk",
                        MigrationConfiguration::setTargetPkFileName,
                        MigrationConfiguration::getTargetPkFileName),
                pathMap(
                        "fk",
                        MigrationConfiguration::setTargetFkFileName,
                        MigrationConfiguration::getTargetFkFileName),
                pathMap(
                        "serial",
                        MigrationConfiguration::setTargetSerialFileName,
                        MigrationConfiguration::getTargetSerialFileName),
                pathMap(
                        "synonym",
                        MigrationConfiguration::setTargetSynonymFileName,
                        MigrationConfiguration::getTargetSynonymFileName),
                pathMap(
                        "plcsql_procedure_header",
                        MigrationConfiguration::setTargetAllPlcsqlProcedureHeaderFileName,
                        MigrationConfiguration::getTargetAllPlcsqlProcedureHeaderFileName),
                pathMap(
                        "plcsql_procedure_all",
                        MigrationConfiguration::setTargetAllPlcsqlProcedureFileName,
                        MigrationConfiguration::getTargetAllPlcsqlProcedureFileName),
                pathMap(
                        "plcsql_function_header",
                        MigrationConfiguration::setTargetAllPlcsqlFunctionHeaderFileName,
                        MigrationConfiguration::getTargetAllPlcsqlFunctionHeaderFileName),
                pathMap(
                        "plcsql_function_all",
                        MigrationConfiguration::setTargetAllPlcsqlFunctionFileName,
                        MigrationConfiguration::getTargetAllPlcsqlFunctionFileName),
                pathMap(
                        "updatestatistic",
                        MigrationConfiguration::setTargetUpdateStatisticFileName,
                        MigrationConfiguration::getTargetUpdateStatisticFileName),
                pathMap(
                        "info",
                        MigrationConfiguration::setTargetSchemaFileListName,
                        MigrationConfiguration::getTargetSchemaFileListName));
    }

    static Stream<Arguments> groupedPathMaps() {
        return Stream.of(
                groupedPathMap(
                        "grant",
                        MigrationConfiguration::setTargetGrantFileName,
                        MigrationConfiguration::getTargetGrantFileName),
                groupedPathMap(
                        "plcsql_procedure",
                        MigrationConfiguration::setTargetPlcsqlProcedureFileName,
                        MigrationConfiguration::getTargetPlcsqlProcedureFileName),
                groupedPathMap(
                        "plcsql_function",
                        MigrationConfiguration::setTargetPlcsqlFunctionFileName,
                        MigrationConfiguration::getTargetPlcsqlFunctionFileName));
    }

    private static Arguments pathMap(
            String name,
            BiConsumer<MigrationConfiguration, Map<String, String>> setter,
            Function<MigrationConfiguration, Map<String, String>> getter) {
        return Arguments.of(name, setter, getter);
    }

    private static Arguments groupedPathMap(
            String name,
            BiConsumer<MigrationConfiguration, Map<String, Map<String, String>>> setter,
            Function<MigrationConfiguration, Map<String, Map<String, String>>> getter) {
        return Arguments.of(name, setter, getter);
    }
}
