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

    private static final class Entry {
        final Catalog catalog;
        final SchemaSelection selection;

        Entry(Catalog catalog, SchemaSelection selection) {
            this.catalog = catalog;
            this.selection = selection;
        }
    }

    private final Map<ConnParameters, Entry> cache = new HashMap<>();

    protected void rekey(ConnParameters oldCp, ConnParameters newCp) {
        if (oldCp == null || newCp == null) return;
        Entry entry = cache.remove(oldCp);
        if (entry != null) cache.put(newCp, entry);
    }

    protected Catalog get(ConnParameters cp, SchemaSelection selection) {
        if (cp == null || selection == null) return null;
        Entry entry = cache.get(cp);
        if (entry == null) return null;
        if (entry.selection.equals(selection)) return entry.catalog;
        return null;
    }

    protected void put(ConnParameters cp, SchemaSelection selection, Catalog catalog) {
        if (cp == null || selection == null || catalog == null) return;
        cache.put(cp, new Entry(catalog, selection));
    }

    protected void clear(ConnParameters cp) {
        if (cp == null) return;
        cache.remove(cp);
    }
}
