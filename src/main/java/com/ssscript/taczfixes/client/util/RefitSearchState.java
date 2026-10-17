package com.ssscript.taczfixes.client.util;

/** 改装界面配件搜索的共享状态: 查询串与页码。列表由 {@link RefitSearchListBuilder} 的实现方构建。 */
public final class RefitSearchState {
    private static String query = "";
    private static int page;

    public static boolean active() {
        return !query.isEmpty();
    }

    public static String query() {
        return query;
    }

    public static void setQuery(String value) {
        query = value == null ? "" : value.trim();
        page = 0;
    }

    public static void clear() {
        query = "";
        page = 0;
    }

    public static int page() {
        return page;
    }

    public static void setPage(int value) {
        page = value;
    }

    private RefitSearchState() {
    }
}
