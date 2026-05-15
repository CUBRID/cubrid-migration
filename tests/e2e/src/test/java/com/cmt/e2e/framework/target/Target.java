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

package com.cmt.e2e.framework.target;

import com.cmt.e2e.framework.source.ConnectionConfig;

import java.util.Map;

/**
 * Migration target — either an online CUBRID DB or a CMT {@code unload} dump output. Online targets
 * boot a container on {@link #start()}; dump targets are no-ops at start (CMT owns the output dir).
 *
 * <p>{@link #options()} returns CMT options written into {@code db.conf} as {@code <target>.<key>=
 * <value>} pairs (e.g. {@code add_schema}, {@code split_schema}, {@code one_table_one_file}, {@code
 * file_prefix}). Connection-derived properties (host, port, ...) come from {@link #connection()}
 * and are not part of {@code options()}.
 */
public interface Target extends AutoCloseable {

    void start();

    /** Connection for CMT's {@code <connection id="target">}; null when dumpfile. */
    ConnectionConfig connection();

    boolean isDumpfile();

    /** CMT options for this target ({@code <target>.<key>=<value>} in db.conf). Never null. */
    Map<String, String> options();

    @Override
    void close();
}
