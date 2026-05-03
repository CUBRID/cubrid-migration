package com.cmt.e2e.framework.source;

/**
 * Factory for E2E source databases. Adding a new source: implement a
 * {@code XxxSource} package-private class, add a factory method here,
 * add seed under {@code src/test/resources/db/<engine>/}.
 */
public final class Sources {

    private Sources() {}

    public static Source oracleE2eSeed() { return new OracleSource(); }
    public static Source cubridE2eSeed() { return new CubridSource(); }
}
