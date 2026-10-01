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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.beans.XMLEncoder;
import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@DisplayName("Closer")
class CloserTest {

    @Nested
    @DisplayName("Closeable")
    class ClosingCloseable {

        @Test
        @DisplayName("the resource is closed")
        void resource_isClosed() throws Exception {
            Closeable closeable = mock(Closeable.class);

            Closer.close(closeable);

            verify(closeable).close();
        }

        @Test
        @DisplayName("an IOException from close is swallowed")
        void ioException_isSwallowed() throws Exception {
            Closeable closeable = mock(Closeable.class);
            doThrow(new IOException("closed twice")).when(closeable).close();

            assertThatCode(() -> Closer.close(closeable)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an unchecked exception from close is passed on")
        void runtimeException_propagates() throws Exception {
            Closeable closeable = mock(Closeable.class);
            doThrow(new IllegalStateException("broken")).when(closeable).close();

            assertThatThrownBy(() -> Closer.close(closeable))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("null is ignored")
        void nullResource_isIgnored() {
            assertThatCode(() -> Closer.close((Closeable) null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("ResultSet")
    class ClosingResultSet {

        @Test
        @DisplayName("the result set is closed")
        void resultSet_isClosed() throws Exception {
            ResultSet resultSet = mock(ResultSet.class);

            Closer.close(resultSet);

            verify(resultSet).close();
        }

        @Test
        @DisplayName("an SQLException from close is swallowed")
        void sqlException_isSwallowed() throws Exception {
            ResultSet resultSet = mock(ResultSet.class);
            doThrow(new SQLException("closed twice")).when(resultSet).close();

            assertThatCode(() -> Closer.close(resultSet)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an unchecked exception from close is passed on")
        void runtimeException_propagates() throws Exception {
            ResultSet resultSet = mock(ResultSet.class);
            doThrow(new IllegalStateException("broken")).when(resultSet).close();

            assertThatThrownBy(() -> Closer.close(resultSet))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("null is ignored")
        void nullResultSet_isIgnored() {
            assertThatCode(() -> Closer.close((ResultSet) null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Statement")
    class ClosingStatement {

        @Test
        @DisplayName("the statement is closed")
        void statement_isClosed() throws Exception {
            Statement statement = mock(Statement.class);

            Closer.close(statement);

            verify(statement).close();
        }

        @Test
        @DisplayName("an SQLException from close is swallowed")
        void sqlException_isSwallowed() throws Exception {
            Statement statement = mock(Statement.class);
            doThrow(new SQLException("closed twice")).when(statement).close();

            assertThatCode(() -> Closer.close(statement)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an unchecked exception from close is passed on")
        void runtimeException_propagates() throws Exception {
            Statement statement = mock(Statement.class);
            doThrow(new IllegalStateException("broken")).when(statement).close();

            assertThatThrownBy(() -> Closer.close(statement))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("null is ignored")
        void nullStatement_isIgnored() {
            assertThatCode(() -> Closer.close((Statement) null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Connection")
    class ClosingConnection {

        @Test
        @DisplayName("the connection is closed")
        void connection_isClosed() throws Exception {
            Connection connection = mock(Connection.class);

            Closer.close(connection);

            verify(connection).close();
        }

        @Test
        @DisplayName("an SQLException from close is swallowed")
        void sqlException_isSwallowed() throws Exception {
            Connection connection = mock(Connection.class);
            doThrow(new SQLException("connection lost")).when(connection).close();

            assertThatCode(() -> Closer.close(connection)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an unchecked exception from close is passed on")
        void runtimeException_propagates() throws Exception {
            Connection connection = mock(Connection.class);
            doThrow(new IllegalStateException("broken")).when(connection).close();

            assertThatThrownBy(() -> Closer.close(connection))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("null is ignored")
        void nullConnection_isIgnored() {
            assertThatCode(() -> Closer.close((Connection) null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("XMLEncoder")
    class ClosingXmlEncoder {

        @Test
        @DisplayName("the encoder is closed")
        void encoder_isClosed() {
            XMLEncoder encoder = mock(XMLEncoder.class);

            Closer.close(encoder);

            verify(encoder).close();
        }

        @Test
        @DisplayName("even an unchecked exception from close is swallowed")
        void runtimeException_isSwallowed() {
            XMLEncoder encoder = mock(XMLEncoder.class);
            doThrow(new IllegalStateException("broken")).when(encoder).close();

            assertThatCode(() -> Closer.close(encoder)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null is ignored")
        void nullEncoder_isIgnored() {
            assertThatCode(() -> Closer.close((XMLEncoder) null)).doesNotThrowAnyException();
        }
    }
}
