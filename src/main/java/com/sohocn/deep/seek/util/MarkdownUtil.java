package com.sohocn.deep.seek.util;

import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.markdown.parser.MarkdownParser;
import org.jetbrains.annotations.NotNull;

public class MarkdownUtil {
    public static String render(String markdownText) {
        try {
            // 创建 Markdown 解析器
            MarkdownParser parser = new MarkdownParser(new CommonMarkFlavourDescriptor());
            ASTNode ast = parser.buildMarkdownTreeFromString(markdownText);

            // 自定义 TagRenderer
            HtmlGenerator.TagRenderer tagRenderer = new HtmlGenerator.TagRenderer() {
                @Override
                public @NotNull CharSequence printHtml(@NotNull CharSequence charSequence) {
                    return charSequence;
                }

                @Override
                public @NotNull CharSequence openTag(@NotNull ASTNode astNode, @NotNull CharSequence charSequence,
                    @NotNull CharSequence[] charSequences, boolean b) {
                    StringBuilder tagBuilder = new StringBuilder();
                    tagBuilder.append("<").append(charSequence);

                    // 添加属性
                    for (CharSequence attr : charSequences) {
                        tagBuilder.append(" ").append(attr);
                    }

                    // 如果是自闭合标签，添加 "/>"
                    if (b) {
                        tagBuilder.append(" />");
                    } else {
                        tagBuilder.append(">");
                    }

                    return tagBuilder.toString();
                }

                @Override
                public @NotNull CharSequence closeTag(@NotNull CharSequence charSequence) {
                    return "</" + charSequence + ">";
                }
            };

            // 生成 HTML
            return new HtmlGenerator(markdownText, ast, new CommonMarkFlavourDescriptor(), false)
                .generateHtml(tagRenderer);
        } catch (Exception e) {
            // 如果发生异常，返回原始 Markdown 文本
            return markdownText;
        }
    }
}