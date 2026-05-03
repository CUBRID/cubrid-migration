package com.cmt.e2e.framework.target;

import com.cmt.e2e.framework.source.ConnectionConfig;

/**
 * Migration target — either an online CUBRID DB or a CMT {@code unload}
 * dump output. Online targets boot a container on {@link #start()};
 * dump targets are no-ops at start (CMT owns the output dir).
 */
public interface Target extends AutoCloseable {

    void start();

    /** Connection for CMT's {@code <connection id="target">}; null when dumpfile. */
    ConnectionConfig connection();

    boolean isDumpfile();

    /** Dump-only options ({@code file_prefix}, {@code one_table_one_file}); null when online. */
    DumpfileOptions dumpfileOptions();

    @Override void close();
}
