package com.cmt.e2e.framework.target;

/** Dump-file target settings ({@code <target>.file_prefix},
 *  {@code <target>.one_table_one_file}). */
public record DumpfileOptions(String filePrefix, boolean oneTableOneFile) {
    public DumpfileOptions {
        if (filePrefix == null || filePrefix.isBlank()) {
            throw new IllegalArgumentException("filePrefix must not be blank");
        }
    }
}
