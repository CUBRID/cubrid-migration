package com.cmt.e2e.framework.target;

/** Factory for E2E migration targets — one method per CMT {@code DEST_*}. */
public final class Targets {

    private Targets() {}

    /** {@code DEST_ONLINE} — empty CUBRID 11.4 container; CMT writes as {@code dba}. */
    public static Target cubridOnline() {
        return new CubridOnlineTarget();
    }

    /** {@code DEST_DB_UNLOAD} — CUBRID LoadDB-format dump tree, no container. */
    public static Target unload(String filePrefix, boolean oneTableOneFile) {
        return new DumpFileTarget(new DumpfileOptions(filePrefix, oneTableOneFile));
    }
}
