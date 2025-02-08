package com.sohocn.deep.seek.utils;

import java.awt.*;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

public class StyleUtil {
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
