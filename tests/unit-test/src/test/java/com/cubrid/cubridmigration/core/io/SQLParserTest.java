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
package com.cubrid.cubridmigration.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@DisplayName("SQLParser")
class SQLParserTest {

    private static List<List<String>> parse(String text) throws IOException {
        Recorder recorder = new Recorder(Integer.MAX_VALUE);
        SQLParser.executeSQLFile(new StringReader(text), 10, recorder);
        return recorder.batches;
    }

    @Test
    @DisplayName("semicolons outside quotes end the statements, and the tail is the last one")
    void executeSQLFile_splitsOnSemicolonsOutsideQuotes() throws Exception {
        assertThat(parse("insert into t values('a;b');select 'it''s;';x"))
                .containsExactly(
                        Arrays.asList("insert into t values('a;b');", "select 'it''s;';", "x"));
    }

    @Test
    @DisplayName("semicolons in comments are ignored and the comment stays in the statement")
    void executeSQLFile_ignoresSemicolonsInComments() throws Exception {
        assertThat(parse("/* it's a;b */select 1;-- c;d\nselect 2;select 3;"))
                .containsExactly(
                        Arrays.asList("/* it's a;b */select 1;", "-- c;d\nselect 2;", "select 3;"));
    }

    @Test
    @DisplayName("a minus, slash or star that starts no comment is kept as it is")
    void executeSQLFile_keepsOperatorsThatStartNoComment() throws Exception {
        assertThat(parse("select 3-1;select 4/2;select 2*3;select 1-"))
                .containsExactly(
                        Arrays.asList("select 3-1;", "select 4/2;", "select 2*3;", "select 1-"));
    }

    @Test
    @DisplayName("the callback is asked after each statement and gets a batch when it says so")
    void executeSQLFile_handsOverABatchWhenAsked() throws Exception {
        Recorder recorder = new Recorder(2);

        SQLParser.executeSQLFile(new StringReader("a;b;c;d"), 10, recorder);

        assertThat(recorder.asked).containsExactly(1, 2, 1);
        assertThat(recorder.batches)
                .containsExactly(Arrays.asList("a;", "b;"), Arrays.asList("c;", "d"));
    }

    @Test
    @DisplayName("a blank tail is dropped, and blank input calls nothing")
    void executeSQLFile_dropsABlankTail() throws Exception {
        assertThat(parse("select 1;  \n")).containsExactly(Collections.singletonList("select 1;"));
        assertThat(parse("")).isEmpty();
        assertThat(parse("   ")).isEmpty();
    }

    @Test
    @DisplayName("the reader is closed, also when the callback fails")
    void executeSQLFile_closesTheReader() throws Exception {
        TrackingReader parsed = new TrackingReader("a;");
        TrackingReader failed = new TrackingReader("a;");

        SQLParser.executeSQLFile(parsed, 10, new Recorder(Integer.MAX_VALUE));
        assertThatThrownBy(
                        () ->
                                SQLParser.executeSQLFile(
                                        failed,
                                        10,
                                        new Recorder(1) {
                                            @Override
                                            public void executeSQLs(
                                                    List<String> sqlList, long size) {
                                                throw new IllegalStateException("import failed");
                                            }
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(parsed.closed).isTrue();
        assertThat(failed.closed).isTrue();
    }

    @Test
    @DisplayName("a comment ending in two stars and a slash does not end there")
    void executeSQLFile_missesACommentEndAfterTwoStars() throws Exception {
        // DEFECT: the char after a star is read ahead and only checked for a slash, so in "**/"
        // the second star is used up, the end is missed and the statements after it stay in the
        // comment
        // - see SQLParser.executeSQLFile()
        assertThat(parse("/* a **/ select 1; select 2;"))
                .containsExactly(Collections.singletonList("/* a **/ select 1; select 2;"));
    }

    @Test
    @DisplayName("a line comment ending in a dash runs on into the next line")
    void executeSQLFile_missesALineEndRightAfterADash() throws Exception {
        // DEFECT: the char after a dash is read ahead, so a line break right after a dash is used
        // up and never ends the line comment, which swallows the next statement
        // - see SQLParser.executeSQLFile()
        assertThat(parse("-- note -\nselect 1;\nselect 2;"))
                .containsExactly(Collections.singletonList("-- note -\nselect 1;\nselect 2;"));
    }

    private static class Recorder implements SQLParser.ISQLParsingCallback {
        private final int batchSize;
        private final List<Integer> asked = new ArrayList<>();
        private final List<List<String>> batches = new ArrayList<>();

        Recorder(int batchSize) {
            this.batchSize = batchSize;
        }

        @Override
        public void executeSQLs(List<String> sqlList, long size) {
            batches.add(new ArrayList<>(sqlList));
        }

        @Override
        public boolean isCommitNow(int sqlsSize) {
            asked.add(sqlsSize);
            return sqlsSize >= batchSize;
        }
    }

    private static final class TrackingReader extends StringReader {
        private boolean closed;

        TrackingReader(String text) {
            super(text);
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }
}
