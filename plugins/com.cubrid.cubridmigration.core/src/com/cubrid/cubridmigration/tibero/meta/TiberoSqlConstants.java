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
package com.cubrid.cubridmigration.tibero.meta;

/** TiberoSqlConstants contains all SQL statements used by TiberoSchemaFetcher and its loaders. */
public final class TiberoSqlConstants {
    public static final String SQL_GET_COLUMNS =
            "SELECT T.COLUMN_NAME, T.DATA_TYPE, T.DATA_LENGTH, T.DATA_PRECISION, T.DATA_SCALE,"
                + " T.NULLABLE, T.DATA_DEFAULT, T.CHAR_LENGTH, T.CHAR_USED, T.COLUMN_ID, C.COMMENTS"
                + " FROM ALL_TAB_COLUMNS T LEFT JOIN ALL_COL_COMMENTS C ON C.OWNER=T.OWNER AND"
                + " C.TABLE_NAME=T.TABLE_NAME AND C.COLUMN_NAME=T.COLUMN_NAME WHERE T.OWNER=? AND"
                + " T.TABLE_NAME=? ORDER BY T.COLUMN_ID";

    public static final String SQL_GET_INDEX_COLUMNS =
            "SELECT A.COLUMN_NAME, A.DESCEND, B.COLUMN_EXPRESSION FROM ALL_IND_COLUMNS A LEFT JOIN"
                + " ALL_IND_EXPRESSIONS B ON A.TABLE_OWNER=B.TABLE_OWNER AND"
                + " A.TABLE_NAME=B.TABLE_NAME AND A.INDEX_NAME=B.INDEX_NAME AND"
                + " A.COLUMN_POSITION=B.COLUMN_POSITION  WHERE A.TABLE_OWNER=? AND A.TABLE_NAME=?"
                + " AND A.INDEX_NAME=? ORDER BY A.COLUMN_POSITION";

    public static final String SQL_GET_PART_COLUMN =
            "SELECT * FROM ALL_PART_KEY_COLUMNS WHERE OBJECT_TYPE='TABLE' AND OWNER=? "
                    + " ORDER BY NAME, COLUMN_POSITION";

    public static final String SQL_GET_PART_TABLES =
            "SELECT T.* FROM ALL_PART_TABLES T WHERE T.OWNER=? ORDER BY TABLE_NAME";

    public static final String SQL_GET_PARTITIONS =
            "SELECT TABLE_NAME, PARTITION_NAME, BOUND, PARTITION_NO "
                    + "FROM ALL_TAB_PARTITIONS WHERE OWNER=? "
                    + "ORDER BY TABLE_NAME, PARTITION_NO";

    public static final String SQL_GET_SUB_PART_TABLES =
            "SELECT TABLE_NAME, PARTITION_NAME, SUBPARTITION_NAME, BOUND,"
                    + " SUBPARTITION_NO  FROM ALL_TAB_SUBPARTITIONS WHERE OWNER=? ORDER BY"
                    + " TABLE_NAME, SUBPARTITION_NO";

    public static final String SQL_GET_SUBPART_KEY_COLUMN =
            "SELECT * FROM ALL_SUBPART_KEY_COLUMNS WHERE OBJECT_TYPE='TABLE' AND OWNER=? "
                    + " ORDER BY NAME, COLUMN_POSITION";

    public static final String SQL_GET_TABLE_INDEX =
            "SELECT INDEX_NAME, INDEX_TYPE, UNIQUENESS FROM ALL_INDEXES A  WHERE A.TABLE_OWNER=?"
                    + " AND A.TABLE_NAME=? AND A.INDEX_NAME NOT IN (SELECT C.CONSTRAINT_NAME FROM"
                    + " ALL_CONSTRAINTS C WHERE C.CONSTRAINT_TYPE='P' AND C.OWNER=A.TABLE_OWNER AND"
                    + " C.TABLE_NAME=A.TABLE_NAME) AND UPPER(A.INDEX_TYPE) <> 'LOB' ORDER BY"
                    + " A.INDEX_NAME";

    public static final String SQL_SHOW_ALL_OBJECTS =
            "SELECT NAME FROM ALL_SOURCE S "
                    + "WHERE S.TYPE=? AND S.OWNER=? AND NOT S.NAME LIKE 'BIN$%' "
                    + "AND NOT S.NAME LIKE 'MLOG$%' AND NOT S.NAME LIKE 'RUPD$%'";

    public static final String SQL_SHOW_DDL = "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM dual";

    public static final String SQL_SHOW_SEQUENCES =
            "SELECT S.* FROM ALL_SEQUENCES S WHERE S.SEQUENCE_OWNER=? AND NOT S.SEQUENCE_NAME LIKE"
                    + " 'BIN$%' AND NOT S.SEQUENCE_NAME LIKE 'MLOG$%' AND NOT S.SEQUENCE_NAME LIKE"
                    + " 'RUPD$%' ";

    public static final String SQL_SHOW_SYNONYM =
            "SELECT SYNONYM_NAME, ORG_OBJECT_OWNER, ORG_OBJECT_NAME FROM ALL_SYNONYMS WHERE"
                    + " OWNER=?";

    public static final String SQL_SHOW_VIEW_QUERYTEXT =
            "SELECT TEXT from ALL_VIEWS WHERE OWNER=? AND VIEW_NAME=?";

    public static final String SQL_GET_ALL_VIEW_QUERYTEXTS =
            "SELECT VIEW_NAME, TEXT from ALL_VIEWS WHERE OWNER=?";

    public static final String SQL_GET_VIEW_COMMENT =
            "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=? AND " + "TABLE_NAME=?";

    public static final String SQL_GET_VIEW_COLUMN_COMMENT =
            "SELECT COMMENTS FROM ALL_COL_COMMENTS WHERE OWNER=? AND "
                    + "TABLE_NAME=? AND COLUMN_NAME=?";

    public static final String SQL_GET_TABLE_COMMENT =
            "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=? AND " + "TABLE_NAME=?";

    public static final String SQL_GET_ALL_TAB_COMMENTS =
            "SELECT TABLE_NAME, COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=?";

    public static final String SQL_SHOW_GRANT_TABLE =
            "SELECT P.GRANTEE, P.OWNER, P.TABLE_NAME, P.GRANTOR, P.PRIVILEGE, P.GRANTABLE"
                    + " FROM USER_TAB_PRIVS P, ALL_TABLES T"
                    + " WHERE P.TABLE_NAME=T.TABLE_NAME"
                    + " AND P.OWNER=T.OWNER"
                    + " AND P.GRANTEE=?";

    public static final String SQL_SHOW_GRANT_VIEW =
            "SELECT P.GRANTEE, P.OWNER, P.TABLE_NAME, P.GRANTOR, P.PRIVILEGE, P.GRANTABLE"
                    + " FROM USER_TAB_PRIVS P, ALL_VIEWS V"
                    + " WHERE P.TABLE_NAME=V.VIEW_NAME"
                    + " AND P.OWNER=V.OWNER"
                    + " AND P.GRANTEE=?";

    public static final String SQL_GET_ENABLED_PK =
            "SELECT acc.COLUMN_NAME, ac.CONSTRAINT_NAME AS PK_NAME FROM ALL_CONSTRAINTS ac JOIN"
                + " ALL_CONS_COLUMNS acc ON ac.OWNER = acc.OWNER AND ac.CONSTRAINT_NAME ="
                + " acc.CONSTRAINT_NAME WHERE ac.CONSTRAINT_TYPE = 'P' AND ac.STATUS = 'ENABLED'"
                + " AND ac.OWNER = ? AND ac.TABLE_NAME = ? ORDER BY acc.POSITION";

    public static final String SQL_GET_ENABLED_FKS =
            "SELECT fk.constraint_name AS FK_NAME, fk.delete_rule AS DELETE_RULE,"
                + " fk_col.column_name AS FK_COLUMN_NAME, pk_col.table_name AS PK_TABLE_NAME,"
                + " pk_col.column_name AS PK_COLUMN_NAME FROM all_constraints fk JOIN"
                + " all_cons_columns fk_col ON fk.owner = fk_col.owner AND fk.constraint_name ="
                + " fk_col.constraint_name JOIN all_cons_columns pk_col ON fk.r_owner ="
                + " pk_col.owner AND fk.r_constraint_name = pk_col.constraint_name AND"
                + " fk_col.position = pk_col.position WHERE fk.owner = ? AND fk.table_name = ? AND"
                + " fk.constraint_type = 'R' AND fk.status = 'ENABLED' ORDER BY fk.constraint_name,"
                + " fk_col.position";

    public static final String SQL_GET_PROCEDURE_DDL =
            "SELECT TEXT FROM ALL_SOURCE WHERE OWNER = ? AND NAME = ? AND TYPE = ? ORDER BY"
                    + " LINE";

    public static final String SQL_GET_PROCEDURE_METADATA =
            "SELECT o.owner, o.object_name, p.authid, o.object_type"
                    + " FROM all_objects o LEFT JOIN all_procedures p"
                    + " ON p.owner = o.owner"
                    + " AND p.object_name = o.object_name"
                    + " AND p.procedure_name IS NULL"
                    + " WHERE o.owner = ?"
                    + " AND o.object_type IN ('PROCEDURE', 'FUNCTION')";

    private TiberoSqlConstants() {
        // Prevent instantiation
    }
}
