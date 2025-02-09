package com.sohocn.deep.seek.util;

import java.awt.*;

import javax.swing.*;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

public class LayoutUtil {
    public static JLabel JLabel(String labelName) {
        JLabel jLabel = new JLabel(labelName);
        jLabel.setForeground(JBColor.GRAY);
        jLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        jLabel.setBorder(JBUI.Borders.empty(4, 2));

        return jLabel;
    }

    public static Color backgroundColor() {
        return new JBColor(Gray._250, Gray._30); // Light/Dark 统一背景色
    }

    public static Color inputBackgroundColor() {
        return new JBColor(Gray._245, Gray._43); // Light/Dark 输入框和消息背景
    }

    public static Color borderColor() {
        return new JBColor(Gray._200, Gray._100); // Light/Dark 边框颜色
    }
}
