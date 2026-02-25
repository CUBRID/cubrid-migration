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

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

class TiberoCommentQueryLoader {

    private static final Logger LOG = LogUtil.getLogger(TiberoCommentQueryLoader.class);

    String getTableComment(
            Connection conn, String errorMessage, String schemaName, String objectName) {
        try (PreparedStatement pstmt = conn.prepareStatement(SQL_GET_TABLE_COMMENT)) {
            pstmt.setString(1, schemaName);
            pstmt.setString(2, objectName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("COMMENTS");
                }
            }
            return "";
        } catch (Exception e) {
            LOG.error(errorMessage, e);
            return null;
        }
    }

    String getViewComment(
            Connection conn, String errorMessage, String schemaName, String viewName) {
        try (PreparedStatement pstmt = conn.prepareStatement(SQL_GET_VIEW_COMMENT)) {
            pstmt.setString(1, schemaName);
            pstmt.setString(2, viewName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("COMMENTS");
                }
            }
            return "";
        } catch (Exception e) {
            LOG.error(errorMessage, e);
            return null;
        }
    }

    String getViewColumnComment(
            Connection conn,
            String errorMessage,
            String schemaName,
            String viewName,
            String columnName) {
        try (PreparedStatement pstmt = conn.prepareStatement(SQL_GET_VIEW_COLUMN_COMMENT)) {
            pstmt.setString(1, schemaName);
            pstmt.setString(2, viewName);
            pstmt.setString(3, columnName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("COMMENTS");
                }
            }
            return "";
        } catch (Exception e) {
            LOG.error(errorMessage, e);
            return null;
        }
    }

    String getViewQueryText(Connection conn, String schemaName, String viewName)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_SHOW_VIEW_QUERYTEXT)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, viewName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("TEXT");
                }
            }
            return null;
        }
    }

    Map<String, String> findAllTabComments(Connection conn, String schemaName) {
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement pstmt = conn.prepareStatement(SQL_GET_ALL_TAB_COMMENTS)) {
            pstmt.setString(1, schemaName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("TABLE_NAME"), rs.getString("COMMENTS"));
                }
            }
        } catch (SQLException e) {
            LOG.error("Query all comments error", e);
        }
        return result;
    }

    Map<String, String> findAllViewQuerySpecs(Connection conn, String schemaName) {
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement pstmt = conn.prepareStatement(SQL_GET_ALL_VIEW_QUERYTEXTS)) {
            pstmt.setString(1, schemaName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("VIEW_NAME"), rs.getString("TEXT"));
                }
            }
        } catch (SQLException e) {
            LOG.error("Query all view query specs error", e);
        }
        return result;
    }
}
