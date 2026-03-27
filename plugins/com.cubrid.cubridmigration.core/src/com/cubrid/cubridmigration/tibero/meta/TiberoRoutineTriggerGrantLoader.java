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
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.cubrid.CUBRIDSQLHelper;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class TiberoRoutineTriggerGrantLoader {

    private static final Logger LOG = LogUtil.getLogger(TiberoRoutineTriggerGrantLoader.class);

    private static final Set<String> SUPPORTED_PRIVILEGES =
            Collections.unmodifiableSet(
                    new HashSet<String>(
                            Arrays.asList(
                                    "SELECT", "INSERT", "UPDATE", "DELETE", "ALTER", "INDEX",
                                    "EXECUTE", "ALL")));

    List<TiberoPlsqlProcedure> getAllProcedures(final Connection conn, final String ownerName)
            throws SQLException {
        List<TiberoPlsqlProcedure> procedures = new ArrayList<TiberoPlsqlProcedure>();
        getPlcsqlProcedureMetaData(conn, ownerName, procedures);
        getPlcsqlProcedureDDL(conn, procedures);

        return procedures;
    }

    List<Trigger> getAllTriggers(
            final Connection conn,
            final String dbName,
            final String ownerName,
            final DBObjectFactory factory,
            final String objectTypeTrigger)
            throws SQLException {
        final List<String> list = getRoutines(conn, objectTypeTrigger, ownerName);
        final List<Trigger> triggers = new ArrayList<Trigger>();

        for (String name : list) {
            final Trigger trigger = factory.createTrigger();
            trigger.setName(name);
            final String trigDDL = getObjectDDL(conn, dbName, name, objectTypeTrigger);
            LOG.debug("[VAR]trigDDL={}", trigDDL);

            trigger.setDDL(trigDDL);
            triggers.add(trigger);
        }

        return triggers;
    }

    void buildGrant(Connection conn, Schema schema, DBObjectFactory factory) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.prepareStatement(SQL_SHOW_GRANT_TABLE);
            LOG.debug("[SQL]{}, 1={}", SQL_SHOW_GRANT_TABLE, schema.getName());

            stmt.setString(1, schema.getName().toUpperCase());
            rs = stmt.executeQuery();
            while (rs.next()) {
                if (!isSupportPrivilege(rs.getString("PRIVILEGE"))) {
                    continue;
                }

                Grant grant = factory.createGrant();
                grant.setGranteeName(rs.getString("GRANTEE"));
                grant.setOwner(schema.getName());
                grant.setClassOwner(rs.getString("OWNER"));
                grant.setClassName(rs.getString("TABLE_NAME"));
                grant.setGrantorName(rs.getString("GRANTOR"));
                grant.setAuthType(convertPrivilegeTibero2Cubrid(rs.getString("PRIVILEGE")));
                grant.setGrantable(rs.getString("GRANTABLE").equals("YES") ? true : false);
                grant.setSourceObjectOwner(grant.getClassOwner());
                grant.setDDL(CUBRIDSQLHelper.getInstance(null).getGrantDDL(grant, true));
                schema.addGrant(grant);
            }

            Closer.close(rs);
            Closer.close(stmt);

            stmt = conn.prepareStatement(SQL_SHOW_GRANT_VIEW);
            LOG.debug("[SQL]{}, 1={}", SQL_SHOW_GRANT_VIEW, schema.getName());

            stmt.setString(1, schema.getName().toUpperCase());
            rs = stmt.executeQuery();
            while (rs.next()) {
                Grant grant = factory.createGrant();
                grant.setGranteeName(rs.getString("GRANTEE"));
                grant.setOwner(schema.getName());
                grant.setClassOwner(rs.getString("OWNER"));
                grant.setClassName(rs.getString("TABLE_NAME"));
                grant.setGrantorName(rs.getString("GRANTOR"));
                grant.setAuthType(rs.getString("PRIVILEGE"));
                grant.setGrantable(rs.getString("GRANTABLE").equals("YES") ? true : false);
                grant.setSourceObjectOwner(grant.getClassOwner());
                grant.setDDL(CUBRIDSQLHelper.getInstance(null).getGrantDDL(grant, true));
                schema.addGrant(grant);
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    private void getPlcsqlProcedureDDL(Connection conn, List<TiberoPlsqlProcedure> procedures)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_PROCEDURE_DDL)) {
            for (TiberoPlsqlProcedure proc : procedures) {
                stmt.setString(1, proc.getOwner());
                stmt.setString(2, proc.getName());
                stmt.setString(3, proc.getProcedureType());

                StringBuilder sb = new StringBuilder();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        sb.append(rs.getString("TEXT"));
                    }
                }
                proc.setDDL(normalizeRoutineDDL(sb.toString()));
            }
        }
    }

    static String normalizeRoutineDDL(String text) {
        String ddl = StringUtils.defaultString(text).trim();

        if (StringUtils.startsWithIgnoreCase(ddl, "CREATE ")) {
            return ddl;
        }

        return "CREATE " + ddl;
    }

    private void getPlcsqlProcedureMetaData(
            Connection conn, String ownerName, List<TiberoPlsqlProcedure> procedures)
            throws SQLException {
        ResultSet rs = null;
        try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_PROCEDURE_METADATA)) {
            stmt.setString(1, ownerName);

            rs = stmt.executeQuery();

            while (rs.next()) {
                procedures.add(
                        new TiberoPlsqlProcedure(
                                rs.getString("OWNER"),
                                rs.getString("OBJECT_NAME"),
                                rs.getString("AUTHID"),
                                rs.getString("OBJECT_TYPE")));
            }
        } finally {
            Closer.close(rs);
        }
    }

    private String getObjectDDL(
            final Connection conn,
            final String schemaName,
            final String objectName,
            final String objectType)
            throws SQLException {
        if (StringUtils.isBlank(objectName)) {
            throw new IllegalArgumentException("The tibero object name is null!");
        }

        PreparedStatement preStmt = null;
        ResultSet rs = null;
        try {
            preStmt = conn.prepareStatement(SQL_SHOW_DDL);
            preStmt.setString(1, objectType);
            preStmt.setString(2, objectName);
            preStmt.setString(3, schemaName);
            LOG.debug(
                    "[SQL]{}, 1={}, 2={}, 3={}", SQL_SHOW_DDL, objectType, objectName, schemaName);

            rs = preStmt.executeQuery();

            String ddl = "";
            while (rs.next()) {
                ddl = rs.getString(1);
            }
            return ddl;
        } catch (Exception ex) {
            LOG.error("Get Tibero Object DDL error:{}", objectName, ex);
            return "";
        } finally {
            Closer.close(rs);
            Closer.close(preStmt);
        }
    }

    private List<String> getRoutines(
            final Connection conn, final String type, final String ownerName) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement(SQL_SHOW_ALL_OBJECTS);
            stmt.setString(1, type);
            stmt.setString(2, ownerName);
            LOG.debug("[SQL]{}, 1={}, 2={}", SQL_SHOW_ALL_OBJECTS, type, ownerName);
            rs = stmt.executeQuery();
            final Set<String> list = new HashSet<String>();
            while (rs.next()) {
                list.add(rs.getString(1));
            }
            LOG.debug("[VAR]list={}", list.size());
            return new ArrayList<String>(list);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    private boolean isSupportPrivilege(String privilege) {
        return SUPPORTED_PRIVILEGES.contains(privilege);
    }

    private String convertPrivilegeTibero2Cubrid(String privilege) {
        if (privilege.equals("ALL")) {
            return "ALL PRIVILEGES";
        }
        return privilege;
    }
}
