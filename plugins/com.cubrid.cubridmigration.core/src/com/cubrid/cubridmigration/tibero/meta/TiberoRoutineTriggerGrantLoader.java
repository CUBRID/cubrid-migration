package com.cubrid.cubridmigration.tibero.meta;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class TiberoRoutineTriggerGrantLoader {

    private static final Logger LOG = LogUtil.getLogger(TiberoRoutineTriggerGrantLoader.class);

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
            final String sqlShowAllObjects,
            final String sqlShowDdl,
            final String objectTypeTrigger)
            throws SQLException {
        final List<String> list =
                getRoutines(conn, objectTypeTrigger, ownerName, sqlShowAllObjects);
        final List<Trigger> triggers = new ArrayList<Trigger>();

        for (String name : list) {
            final Trigger trigger = factory.createTrigger();
            trigger.setName(name);
            final String trigDDL = getObjectDDL(conn, dbName, name, objectTypeTrigger, sqlShowDdl);
            LOG.debug("[VAR]trigDDL={}", trigDDL);

            trigger.setDDL(trigDDL);
            triggers.add(trigger);
        }

        return triggers;
    }

    void buildGrant(
            Connection conn,
            Schema schema,
            DBObjectFactory factory,
            String sqlShowGrantTable,
            String sqlShowGrantView)
            throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.prepareStatement(sqlShowGrantTable);
            LOG.debug("[SQL]{}, 1={}", sqlShowGrantTable, schema.getName());

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

            stmt = conn.prepareStatement(sqlShowGrantView);
            LOG.debug("[SQL]{}, 1={}", sqlShowGrantView, schema.getName());

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
        String sql =
                "SELECT TEXT FROM ALL_SOURCE WHERE OWNER = ? AND NAME = ? AND TYPE = ? ORDER BY"
                        + " LINE";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (TiberoPlsqlProcedure proc : procedures) {
                stmt.setString(1, proc.getOwner());
                stmt.setString(2, proc.getName());
                stmt.setString(3, proc.getProcedureType());

                StringBuilder sb = new StringBuilder();
                sb.append("CREATE ");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        sb.append(rs.getString("TEXT"));
                    }
                }
                proc.setDDL(sb.toString());
            }
        }
    }

    private void getPlcsqlProcedureMetaData(
            Connection conn, String ownerName, List<TiberoPlsqlProcedure> procedures)
            throws SQLException {
        String sql =
                "SELECT o.owner, o.object_name, p.authid, o.object_type"
                        + " FROM all_objects o LEFT JOIN all_procedures p"
                        + " ON p.owner = o.owner"
                        + " AND p.object_name = o.object_name"
                        + " AND p.procedure_name IS NULL"
                        + " WHERE o.owner = ?"
                        + " AND o.object_type IN ('PROCEDURE', 'FUNCTION')";

        ResultSet rs = null;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
            final String objectType,
            final String sqlShowDdl)
            throws SQLException {
        if (StringUtils.isBlank(objectName)) {
            throw new IllegalArgumentException("The tibero object name is null!");
        }

        PreparedStatement preStmt = null;
        ResultSet rs = null;
        try {
            preStmt = conn.prepareStatement(sqlShowDdl);
            preStmt.setString(1, objectType);
            preStmt.setString(2, objectName);
            preStmt.setString(3, schemaName);
            LOG.debug("[SQL]{}, 1={}, 2={}, 3={}", sqlShowDdl, objectType, objectName, schemaName);

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
            final Connection conn,
            final String type,
            final String ownerName,
            final String sqlShowAllObjects)
            throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.prepareStatement(sqlShowAllObjects);
            stmt.setString(1, type);
            stmt.setString(2, ownerName);
            LOG.debug("[SQL]{}, 1={}, 2={}", sqlShowAllObjects, type, ownerName);
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
        if (privilege.equals("SELECT")
                || privilege.equals("INSERT")
                || privilege.equals("UPDATE")
                || privilege.equals("DELETE")
                || privilege.equals("ALTER")
                || privilege.equals("INDEX")
                || privilege.equals("EXECUTE")
                || privilege.equals("ALL")) {
            return true;
        }
        return false;
    }

    private String convertPrivilegeTibero2Cubrid(String privilege) {
        if (privilege.equals("ALL")) {
            return "ALL PRIVILEGES";
        }
        return privilege;
    }
}
