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
package com.cubrid.cubridmigration.testutil;

import com.cubrid.common.configuration.classloader.ClassLoaderManager;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Snapshot of the class loaders ClassLoaderManager holds, so a test can drop the ones it added. */
public final class ClassLoaderManagerState {

    private final Set<String> paths;

    private ClassLoaderManagerState(Set<String> paths) {
        this.paths = paths;
    }

    public static ClassLoaderManagerState capture() {
        ClassLoaderManager manager = ClassLoaderManager.getInstance();
        synchronized (manager) {
            return new ClassLoaderManagerState(new HashSet<>(cache(manager).keySet()));
        }
    }

    public void restore() {
        ClassLoaderManager manager = ClassLoaderManager.getInstance();
        synchronized (manager) {
            Iterator<Map.Entry<String, ClassLoader>> entries = cache(manager).entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<String, ClassLoader> entry = entries.next();
                if (!paths.contains(entry.getKey())) {
                    entries.remove();
                    close(entry.getValue());
                }
            }
        }
    }

    private static void close(ClassLoader loader) {
        if (loader instanceof Closeable) {
            try {
                ((Closeable) loader).close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    // ClassLoaderManager never forgets a loader, so the cache is only reachable through the field.
    @SuppressWarnings("unchecked")
    private static Map<String, ClassLoader> cache(ClassLoaderManager manager) {
        try {
            Field field = ClassLoaderManager.class.getDeclaredField("path2Loader");
            field.setAccessible(true);
            return (Map<String, ClassLoader>) field.get(manager);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
