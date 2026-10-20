package com.devsuperior.dscommerce.services;

final class ProductNameFilter {
    private ProductNameFilter() { }

    static String normalize(String name) {
        if (name == null) return "";
        int start = 0;
        int end = name.length();
        while (start < end) {
            int codePoint = name.codePointAt(start);
            if (!isExternalSpace(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = name.codePointBefore(end);
            if (!isExternalSpace(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return name.substring(start, end);
    }

    private static boolean isExternalSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
