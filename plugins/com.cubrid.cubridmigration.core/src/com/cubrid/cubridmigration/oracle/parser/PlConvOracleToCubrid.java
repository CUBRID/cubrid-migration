/*
 * Copyright (C) 2008 Search Solution Corporation.
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
package com.cubrid.cubridmigration.oracle.parser;

import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.oracle.parser.antlr4gen.*;
import java.util.List;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.apache.log4j.Logger;

public class PlConvOracleToCubrid {

    private static final Logger log = LogUtil.getLogger(PlConvOracleToCubrid.class);

    public static ProcedureDDL getProcedureDDL(String text, boolean changeDataType) {

        CharStream input = CharStreams.fromString(text);
        PloLexer lexer = new PloLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PloParser parser = new PloParser(tokens);

        SyntaxErrorIndicator sei = new SyntaxErrorIndicator();
        parser.removeErrorListeners();
        parser.addErrorListener(sei);

        ParseTree tree = parser.sql_script();
        if (sei.hasError) {
            log.error(new SyntaxError(sei.line, sei.column, sei.msg));
        }

        OffsetCollector oc = new OffsetCollector();
        ParseTreeWalker.DEFAULT.walk(oc, tree);

        String header = text.substring(0, oc.bodyStartOffset);
        assert header != null;
        String body = text.substring(oc.bodyStartOffset);
        assert body != null;

        header = header.trim();
        body = body.trim();

        if (changeDataType) {
            header = convertTypes(header, oc.headerConversion);
            body = convertTypes(body, oc.bodyConversion);
        }

        return new ProcedureDDL(header, body, oc.unsupportedTypeFound);
    }

    private static String convertTypes(String text, List<OffsetCollector.Conversion> conv) {

        if (conv.size() == 0) {
            return text;
        }

        int curr = 0;
        StringBuilder sb = new StringBuilder();
        for (OffsetCollector.Conversion c : conv) {
            sb.append(text.substring(curr, c.start));
            sb.append(c.converter);
            curr = c.start + c.length;
        }

        sb.append(text.substring(curr));

        return sb.toString();
    }

    private static class SyntaxErrorIndicator extends BaseErrorListener {

        boolean hasError;
        int line;
        int column;
        String msg;

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {

            this.hasError = true;
            this.line = line;
            this.column = charPositionInLine + 1; // charPositionInLine starts from 0
            this.msg = msg;
        }
    }
}
