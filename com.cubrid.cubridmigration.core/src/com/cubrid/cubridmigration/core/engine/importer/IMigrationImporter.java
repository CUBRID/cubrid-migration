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
package com.cubrid.cubridmigration.core.engine.importer;

import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Function;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlFunction;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlProcedure;
import com.cubrid.cubridmigration.core.dbobject.Procedure;
import com.cubrid.cubridmigration.core.dbobject.Record;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import java.util.List;

/**
 * IImporter interface definition.
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-8-25 created by Kevin Cao
 */
public interface IMigrationImporter {

    /**
     * Execute DDL.
     *
     * @param sql String
     */
    public void executeDDL(String sql);

    /**
     * Create foreign key
     *
     * @param fk FK
     */
    public void createFK(FK fk);

    /**
     * Create function
     *
     * @param function Function
     */
    public void createFunction(Function function);

    /**
     * Create plcsqlFunction header
     *
     * @param plcsqlFunction PlcsqlFunction
     */
    public void createPlcsqlFunctionHeader(PlcsqlFunction plcsqlFunction);

    /**
     * Create plcsqlFunction body
     *
     * @param plcsqlFunction PlcsqlFunction
     */
    public void createPlcsqlFunctionBody(PlcsqlFunction plcsqlFunction);

    /**
     * Create index
     *
     * @param index Index
     */
    public void createIndex(Index index);

    /**
     * Create primary key
     *
     * @param pk PK
     */
    public void createPK(PK pk);

    /**
     * Create procedure
     *
     * @param procedure Procedure
     */
    public void createProcedure(Procedure procedure);

    /**
     * Create plcsqlProcedure header
     *
     * @param plcsqlProcedure PlcsqlProcedure
     */
    public void createPlcsqlProcedureHeader(PlcsqlProcedure plcsqlProcedure);

    /**
     * Create plcsqlProcedure body
     *
     * @param plcsqlProcedure PlcsqlProcedure
     */
    public void createPlcsqlProcedureBody(PlcsqlProcedure plcsqlProcedure);

    /**
     * Create sequence
     *
     * @param sq Sequence
     */
    public void createSequence(Sequence sq);

    /**
     * Create synonym
     *
     * @param sn Synonym
     */
    public void createSynonym(Synonym sn);

    /**
     * Create grant
     *
     * @param gr Grant
     */
    public void createGrant(Grant gr);

    /**
     * Create Schema
     *
     * @param Schema schema
     */
    public void createSchema(Schema schema);

    /**
     * Create table
     *
     * @param table Table
     */
    public void createTable(Table table);

    /**
     * Create triggers
     *
     * @param trigger Trigger
     */
    public void createTriggers(Trigger trigger);

    /**
     * Create view
     *
     * @param view View
     */
    public void createView(View view);

    /**
     * Create view alter
     *
     * @param view View
     */
    public void alterView(View view);

    /**
     * Import records
     *
     * @param stc SourceTableConfig
     * @param records List<Record>
     * @return success count
     */
    public int importRecords(SourceTableConfig stc, List<Record> records);
}
