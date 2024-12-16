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

import com.cubrid.cubridmigration.oracle.parser.antlr4gen.PloParser;
import com.cubrid.cubridmigration.oracle.parser.antlr4gen.PloParserBaseListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

public class OffsetCollector extends PloParserBaseListener {

    public int bodyStartOffset;
    public List<Conversion> headerConversion = new LinkedList<>();
    public List<Conversion> bodyConversion = new LinkedList<>();
    public boolean unsupportedTypeFound;

    public static class Conversion {
        public final int start;
        public final int length;
        public final String converter;

        Conversion(int start, int length, String converter) {
            this.start = start;
            this.length = length;
            this.converter = converter;
        }
    }

    @Override
    public void enterRoutine_definition(PloParser.Routine_definitionContext ctx) {

        if (bodyStartOffset == 0) {

            // NOTE: More than one routine definitions can exist because of local procedures and
            // functions
            //   Only the first entered (bodyStartOffset == 0) is the outermost one that we are
            // interested in.

            Token isOrAs;
            if (ctx.IS() == null) {
                assert ctx.AS() != null;
                isOrAs = ctx.AS().getSymbol();
            } else {
                isOrAs = ctx.IS().getSymbol();
            }
            bodyStartOffset = isOrAs.getStartIndex();
            assert bodyStartOffset > 0;
        }
    }

    @Override
    public void enterNative_datatype(PloParser.Native_datatypeContext ctx) {

        int start = 0, length = 0;
        String converter = null;

        Token t = getFirstToken(ctx);
        if (unsupportedTypes.contains(t.getType())) {
            start = t.getStartIndex();
            length = t.getStopIndex() + 1 - start;
            converter = "/* " + t.getText() + " (unsupported) */";
            unsupportedTypeFound = true;
        } else if (supportedTypes.contains(t.getType())) {
            start = t.getStartIndex();
            length = t.getStopIndex() + 1 - start;
            converter = typeMap.get(t.getText().toUpperCase());
            assert converter != null;
        }

        if (converter != null) {

            List<Conversion> target;
            if (start > bodyStartOffset) {
                start -= bodyStartOffset;
                assert start > 0;
                target = bodyConversion;
            } else {
                target = headerConversion;
            }

            target.add(new Conversion(start, length, converter));
        }
    }

    // --------------------------------------------------------
    // PRIVATE
    // --------------------------------------------------------

    private static Token getFirstToken(ParseTree pt) {
        ParseTree child = pt.getChild(0);
        if (child == null) {
            return null;
        }

        if (child instanceof TerminalNode) {
            return ((TerminalNode) child).getSymbol();
        } else {
            assert child instanceof RuleNode;
            return getFirstToken(child);
        }
    }

    private static Set<Integer> unsupportedTypes = new HashSet<>();

    static {
        unsupportedTypes.add(PloParser.LONG);
        unsupportedTypes.add(PloParser.LONG_RAW);
        unsupportedTypes.add(PloParser.RAW);
        unsupportedTypes.add(PloParser.INTERVAL_YEAR_TO_MONTH);
        unsupportedTypes.add(PloParser.INTERVAL_DAY_TO_SECOND);
        unsupportedTypes.add(PloParser.TIMESTAMP_WITH_TIME_ZONE);
        unsupportedTypes.add(PloParser.TIMESTAMP_WITH_LOCAL_TIME_ZONE);
        unsupportedTypes.add(PloParser.BLOB);
        unsupportedTypes.add(PloParser.CLOB);
        unsupportedTypes.add(PloParser.NCLOB);
        unsupportedTypes.add(PloParser.BFILE);
        unsupportedTypes.add(PloParser.ROWID);
        unsupportedTypes.add(PloParser.UROWID);
    }

    private static Set<Integer> supportedTypes = new HashSet<>();

    static {
        supportedTypes.add(PloParser.VARCHAR2);
        supportedTypes.add(PloParser.NCHAR);
        supportedTypes.add(PloParser.NVARCHAR2);
        supportedTypes.add(PloParser.NUMBER);
        supportedTypes.add(PloParser.FLOAT);
        supportedTypes.add(PloParser.BINARY_FLOAT);
        supportedTypes.add(PloParser.BINARY_DOUBLE);
        supportedTypes.add(PloParser.DATE);
    }

    private static Map<String, String> typeMap = new HashMap<>();

    static {
        typeMap.put("VARCHAR2", "VARCHAR");
        typeMap.put("NCHAR", "CHAR");
        typeMap.put("NVARCHAR2", "VARCHAR");
        typeMap.put("NUMBER", "NUMERIC");
        typeMap.put("FLOAT", "NUMERIC");
        typeMap.put("BINARY_FLOAT", "FLOAT");
        typeMap.put("BINARY_DOUBLE", "DOUBLE");
        typeMap.put("DATE", "DATETIME");
    }
}
