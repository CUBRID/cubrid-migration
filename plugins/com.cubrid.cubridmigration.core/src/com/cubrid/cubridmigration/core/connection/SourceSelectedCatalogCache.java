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
package com.cubrid.cubridmigration.core.connection;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.engine.config.SchemaSelection;

import java.util.HashMap;
import java.util.Map;

/** Cache of detailed source Catalog per connection and schema selection. */
public class SourceSelectedCatalogCache {

    private final Map<ConnParameters, Map<SchemaSelection, Catalog>> cache = new HashMap<>();

    /** Returns cached Catalog for given connection and schema selection, or null if not cached. */
    protected Catalog get(ConnParameters cp, SchemaSelection selection) {
        if (cp == null || selection == null) {
            return null;
        }
        Map<SchemaSelection, Catalog> bySelection = cache.get(cp);
        if (bySelection == null) {
            return null;
        }
        return bySelection.get(selection);
    }

    /** Puts Catalog into cache for given connection and schema selection. */
    protected void put(ConnParameters cp, SchemaSelection selection, Catalog catalog) {
        if (cp == null || selection == null || catalog == null) {
            return;
        }
        Map<SchemaSelection, Catalog> bySelection = cache.get(cp);
        if (bySelection == null) {
            bySelection = new HashMap<SchemaSelection, Catalog>();
            cache.put(cp, bySelection);
        }
        bySelection.put(selection, catalog);
    }

    /** Removes a specific selection entry for given connection. */
    protected void remove(ConnParameters cp, SchemaSelection selection) {
        if (cp == null || selection == null) {
            return;
        }
        Map<SchemaSelection, Catalog> bySelection = cache.get(cp);
        if (bySelection == null) {
            return;
        }
        bySelection.remove(selection);
        if (bySelection.isEmpty()) {
            cache.remove(cp);
        }
    }

    /** Clears all cached selections for given connection. */
    protected void clear(ConnParameters cp) {
        if (cp == null) {
            return;
        }
        cache.remove(cp);
    }

    /** Clears all entries in this cache. */
    protected void clearAll() {
        cache.clear();
    }

    /**
     * Moves all cached selections from oldCp to newCp. Used when connection is renamed but points
     * to the same physical DB.
     */
    protected void rekey(ConnParameters oldCp, ConnParameters newCp) {
        if (oldCp == null || newCp == null || oldCp == newCp) {
            return;
        }
        Map<SchemaSelection, Catalog> bySelection = cache.remove(oldCp);
        if (bySelection != null) {
            cache.put(newCp, bySelection);
        }
    }
}
