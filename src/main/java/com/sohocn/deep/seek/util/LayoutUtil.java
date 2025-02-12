package com.sohocn.deep.seek.util;

import java.awt.*;
import java.util.Arrays;

import javax.swing.*;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.sohocn.deep.seek.constant.AppConstant;

public class LayoutUtil {
    public static JLabel JLabel(String labelName) {
        JLabel jLabel = new JLabel(labelName);
        jLabel.setForeground(JBColor.GRAY);
        jLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        jLabel.setBorder(JBUI.Borders.empty(4, 2));

        return jLabel;
    }

    public static JComboBox<String> contextSelect() {
        // 创建选择列表
        String[] options = {"0", "1", "2", "3", "4", "5"};
        JComboBox<String> selectList = new ComboBox<>(options);
        selectList.setForeground(JBColor.GRAY);
        selectList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        // 设置下拉框大小
        Dimension comboBoxSize = new Dimension(60, 25); // 设置宽度和高度
        selectList.setPreferredSize(comboBoxSize);
        selectList.setMaximumSize(comboBoxSize);
        selectList.setMinimumSize(comboBoxSize);

        // 获取默认值并设置选中项
        PropertiesComponent instance = PropertiesComponent.getInstance();
        String value = instance.getValue(AppConstant.CONTEXT, "0");

        if (Arrays.asList(options).contains(value)) {
            selectList.setSelectedItem(value);
        } else {
            selectList.setSelectedItem("0"); // 如果无效，设置为默认值 "0"
        }

        // 添加监听器
        selectList.addActionListener(e -> {
            String selectedItem = (String)selectList.getSelectedItem();
            instance.setValue(AppConstant.CONTEXT, selectedItem);
        });

        // 调整边距
        selectList.setBorder(JBUI.Borders.empty(0, 2));

        return selectList;
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
