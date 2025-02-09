package com.sohocn.deep.seek.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class MarkdownUtil {
    private static final Parser parser;
    private static final HtmlRenderer renderer;
    private static final String CSS_STYLES = 
        "<style type='text/css'>" +
        "body { font-family: Arial, sans-serif; margin: 8px; color: #CCCCCC; }" +
        "pre { background-color: #2B2B2B; padding: 16px; border-radius: 6px; overflow-x: auto; margin: 8px 0; }" +
        "code { font-family: 'JetBrains Mono', monospace; color: #A9B7C6; }" +
        "p { margin: 8px 0; }" +
        "a { color: #4A9EEE; }" +
        "ul, ol { margin: 8px 0; padding-left: 24px; }" +
        "li { margin: 4px 0; }" +
        "blockquote { margin: 8px 0; padding-left: 16px; border-left: 4px solid #4A9EEE; color: #A9B7C6; }" +
        "</style>";

    static {
        MutableDataSet options = new MutableDataSet();
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    public static String renderMarkdown(String markdown) {
        try {
            String html = renderer.render(parser.parse(markdown));
            return wrapInHtml(html);
        } catch (Exception e) {
            return wrapInHtml(markdown);
        }
    }

    public static String renderHtml(String content) {
        return wrapInHtml(content);
    }

    private static String wrapInHtml(String content) {
        return "<!DOCTYPE html><html><head>" + CSS_STYLES + 
               "</head><body>" + content + "</body></html>";
    }
} 