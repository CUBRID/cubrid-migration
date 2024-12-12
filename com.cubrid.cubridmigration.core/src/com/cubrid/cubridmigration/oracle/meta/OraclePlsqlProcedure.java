package com.cubrid.cubridmigration.oracle.meta;

public class OraclePlsqlProcedure {

    private final String owner;
    private final String name;
    private final String authid;
    private final String procedureType;
    private String ddl;

    public OraclePlsqlProcedure(String owner, String name, String authid, String procedureType) {
        this.owner = owner;
        this.name = name;
        this.authid = authid;
        this.procedureType = procedureType;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getAuthid() {
        return authid;
    }

    public String getProcedureType() {
        return procedureType;
    }

    public void setDDL(String ddl) {
        this.ddl = ddl;
    }

    public String getDDL() {
        return ddl;
    }
}
