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
package com.cubrid.cubridmigration.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.engine.exception.UserDefinedHandlerException;
import com.cubrid.cubridmigration.testutil.PathUtilsState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

@DisplayName("UserDefinedDataHandlerManager")
class UserDefinedDataHandlerManagerTest {

    private static final String UPPER = "any.jar:" + Upper.class.getName();

    private static final String LOWER_SOURCE =
            "package handler;\n"
                    + "import java.util.Map;\n"
                    + "public class Lower {\n"
                    + "    public Object convert(Map<String, Object> record, String column) {\n"
                    + "        return String.valueOf(record.get(column)).toLowerCase();\n"
                    + "    }\n"
                    + "}\n";

    private Path dir;

    private PathUtilsState paths;

    private UserDefinedDataHandlerManager manager;

    @BeforeEach
    void setUp(@TempDir Path dir) throws Exception {
        this.dir = dir;
        paths = PathUtilsState.capture();
        PathUtils.initPaths(
                dir.resolve("install").toString(),
                dir.resolve("tmp").toString(),
                dir.resolve("ws").toString());
        Constructor<UserDefinedDataHandlerManager> constructor =
                UserDefinedDataHandlerManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        manager = constructor.newInstance();
    }

    @AfterEach
    void restorePaths() {
        paths.restore();
    }

    private void writeLowerJar(String name) throws IOException {
        Path source = dir.resolve("src/handler/Lower.java");
        Files.createDirectories(source.getParent());
        Files.write(source, LOWER_SOURCE.getBytes(StandardCharsets.UTF_8));
        Path classes = Files.createDirectories(dir.resolve("classes"));
        int status =
                ToolProvider.getSystemJavaCompiler()
                        .run(null, null, null, "-d", classes.toString(), source.toString());
        assertThat(status).isZero();
        Path jar = Paths.get(PathUtils.getHandlersDir(), name);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("handler/Lower.class"));
            out.write(Files.readAllBytes(classes.resolve("handler/Lower.class")));
        }
    }

    @Nested
    @DisplayName("putColumnDataHandler()")
    class PutColumnDataHandler {

        @Test
        @DisplayName("a class with convert() in a jar of the handlers folder is registered")
        void jarClass_isRegistered() throws Exception {
            writeLowerJar("lower.jar");

            assertThat(manager.putColumnDataHandler("lower.jar:handler.Lower", true)).isTrue();

            assertThat(manager.getColumnDataHandler("lower.jar:handler.Lower"))
                    .extracting(handler -> handler.getClass().getName())
                    .isEqualTo("handler.Lower");
        }

        @Test
        @DisplayName("a class missing from the named jar gives false")
        void classMissingFromTheJar_givesFalse() throws Exception {
            writeLowerJar("lower.jar");

            assertThat(manager.putColumnDataHandler("other.jar:handler.Lower", true)).isFalse();
            assertThat(manager.getColumnDataHandler("other.jar:handler.Lower")).isNull();
        }

        @Test
        @DisplayName(
                "a class on the class path is found through the parent loader, whatever the jar")
        void classPathClass_isRegistered() {
            assertThat(manager.putColumnDataHandler(UPPER, true)).isTrue();

            assertThat(manager.getColumnDataHandler(UPPER)).isInstanceOf(Upper.class);
        }

        @Test
        @DisplayName("without replace a registered handler is kept, with replace it is made again")
        void replace_makesTheHandlerAgain() {
            manager.putColumnDataHandler(UPPER, true);
            Object first = manager.getColumnDataHandler(UPPER);

            assertThat(manager.putColumnDataHandler(UPPER, false)).isTrue();
            assertThat(manager.getColumnDataHandler(UPPER)).isSameAs(first);
            assertThat(manager.putColumnDataHandler(UPPER, true)).isTrue();
            assertThat(manager.getColumnDataHandler(UPPER))
                    .isNotSameAs(first)
                    .isInstanceOf(Upper.class);
        }

        @Test
        @DisplayName("a class without convert() is not registered and gives false")
        void classWithoutConvert_givesFalse() {
            // DEFECT: getHandlerMethod() throws instead of returning null, so the null check
            // before registering is always true and a class without convert() fails only through
            // the catch that logs it as an error
            // - see UserDefinedDataHandlerManager.putColumnDataHandler()
            String handler = "any.jar:" + NoConvert.class.getName();

            assertThat(manager.putColumnDataHandler(handler, true)).isFalse();
            assertThat(manager.getColumnDataHandler(handler)).isNull();
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("a string that is not a jar and a class, or names no class, gives false")
        @MethodSource(
                "com.cubrid.cubridmigration.core.engine.UserDefinedDataHandlerManagerTest#badHandlers")
        void badHandler_givesFalse(String handler) {
            assertThat(manager.putColumnDataHandler(handler, true)).isFalse();
            assertThat(manager.getColumnDataHandler(handler)).isNull();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("a blank string gives false")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        void blankHandler_givesFalse(String handler) {
            assertThat(manager.putColumnDataHandler(handler, true)).isFalse();
        }
    }

    @Nested
    @DisplayName("handleColumnData()")
    class HandleColumnData {

        @Test
        @DisplayName("convert() gets the record and the column name, and its result comes back")
        void convert_resultComesBack() {
            Map<String, Object> record = new HashMap<>();
            record.put("col1", "test");

            assertThat(manager.handleColumnData(new Upper(), record, "col1")).isEqualTo("TEST");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("a handler that has no convert() throws UserDefinedHandlerException")
        @MethodSource(
                "com.cubrid.cubridmigration.core.engine.UserDefinedDataHandlerManagerTest#handlersWithoutConvert")
        void noConvert_throwsUserDefinedHandlerException(
                String label, Object handler, Class<? extends Throwable> cause) {
            assertThatThrownBy(() -> manager.handleColumnData(handler, new HashMap<>(), "col1"))
                    .isExactlyInstanceOf(UserDefinedHandlerException.class)
                    .hasMessage("Can't convert data by user defined data handler.")
                    .hasCauseExactlyInstanceOf(cause);
        }

        @Test
        @DisplayName("a convert() that throws is wrapped in UserDefinedHandlerException")
        void failingConvert_throwsUserDefinedHandlerException() {
            assertThatThrownBy(
                            () -> manager.handleColumnData(new Failing(), new HashMap<>(), "col1"))
                    .isExactlyInstanceOf(UserDefinedHandlerException.class)
                    .hasMessage("Can't convert data by user defined data handler.")
                    .hasCauseExactlyInstanceOf(InvocationTargetException.class)
                    .hasRootCauseExactlyInstanceOf(IllegalStateException.class);
        }
    }

    static Stream<String> badHandlers() {
        return Stream.of(
                Upper.class.getName(),
                "any.jar:",
                "any.jar:" + Upper.class.getName() + ":more",
                "any.jar:no.such.Handler");
    }

    static Stream<Arguments> handlersWithoutConvert() {
        return Stream.of(
                Arguments.of("null", null, NullPointerException.class),
                Arguments.of(
                        "a class without convert()", new NoConvert(), NoSuchMethodException.class));
    }

    public static class Upper {
        public Object convert(Map<String, Object> record, String column) {
            return String.valueOf(record.get(column)).toUpperCase();
        }
    }

    public static class NoConvert {}

    public static class Failing {
        public Object convert(Map<String, Object> record, String column) {
            throw new IllegalStateException("inside");
        }
    }
}
