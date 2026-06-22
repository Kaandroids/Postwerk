package com.postwerk.util;

import org.jsoup.Jsoup;

/**
 * Utility for converting HTML content to plain text using Jsoup.
 *
 * <p>Strips all HTML tags and returns the visible text content. Used for generating
 * plain-text email snippets and preparing email bodies for AI processing.</p>
 *
 * @since 1.0
 */
public final class HtmlToTextUtil {

    private HtmlToTextUtil() {}

    public static String convert(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.parse(html).text();
    }
}
