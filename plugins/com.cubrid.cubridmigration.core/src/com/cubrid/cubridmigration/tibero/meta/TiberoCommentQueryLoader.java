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
package com.cubrid.cubridmigration.tibero.meta;

import static com.cubrid.cubridmigration.tibero.meta.TiberoSqlConstants.*;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.Closer;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

class TiberoCommentQueryLoader {

    private static final Logger LOG = LogUtil.getLogger(TiberoCommentQueryLoader.class);

    String getComment(Connection conn, String errorMessage, String schemaName, String objectName) {
        return querySingleString(
                conn, SQL_GET_TABLE_COMMENT, "COMMENTS", errorMessage, schemaName, objectName);
    }

    String getViewComment(
            Connection conn, String errorMessage, String schemaName, String viewName) {
        return querySingleString(
                conn, SQL_GET_VIEW_COMMENT, "COMMENTS", errorMessage, schemaName, viewName);
    }

    String getViewColumnComment(
            Connection conn,
            String errorMessage,
            String schemaName,
            String viewName,
            String columnName) {
        return querySingleString(
                conn,
                SQL_GET_VIEW_COLUMN_COMMENT,
                "COMMENTS",
                errorMessage,
                schemaName,
                viewName,
                columnName);
    }

    String getViewQueryText(Connection conn, String schemaName, String viewName)
            throws SQLException {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(SQL_SHOW_VIEW_QUERYTEXT);
            stmt.setString(1, schemaName);
            stmt.setString(2, viewName);
            rs = stmt.executeQuery();
            while (rs.next()) {
                return rs.getString("TEXT");
            }
            return null;
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    Map<String, String> findAllTabComments(Connection conn, String schemaName) {
        return queryMap(conn, SQL_GET_ALL_TAB_COMMENTS, "TABLE_NAME", "COMMENTS", schemaName);
    }

    Map<String, String> findAllViewQuerySpecs(Connection conn, String schemaName) {
        return queryMap(conn, SQL_GET_ALL_VIEW_QUERYTEXTS, "VIEW_NAME", "TEXT", schemaName);
    }

    private Map<String, String> queryMap(
            Connection conn, String sql, String keyColumn, String valueColumn, String... params) {
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setString(i + 1, params[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(keyColumn), rs.getString(valueColumn));
                }
            }
        } catch (SQLException e) {
            LOG.error("Query map error", e);
        }
        return result;
    }

    private String querySingleString(
            Connection conn, String sql, String columnName, String errorMessage, String... params) {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) {
                pstmt.setString(i + 1, params[i]);
            }
            rs = pstmt.executeQuery();

            String value = "";
            while (rs.next()) {
                value = rs.getString(columnName);
            }
            return value;
        } catch (Exception e) {
            LOG.error(errorMessage, e);
            return null;
        } finally {
            Closer.close(rs);
            Closer.close(pstmt);
        }
    }
}
