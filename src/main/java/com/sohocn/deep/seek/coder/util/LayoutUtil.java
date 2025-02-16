package com.sohocn.deep.seek.coder.util;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import javax.swing.*;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.sohocn.deep.seek.coder.constant.AppConstant;

public class LayoutUtil {
    public static JTextArea jTextArea() {
        JTextArea jTextArea = new JTextArea();
        jTextArea.setLineWrap(true);
        jTextArea.setWrapStyleWord(true);
        jTextArea.setRows(5);
        jTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        jTextArea.setBorder(BorderFactory.createLineBorder(LayoutUtil.borderColor()));

        return jTextArea;
    }

    public static ComboBox<String> comboBox(Map<String, String> map, String filed, boolean edit) {
        ComboBox<String> comboBox = new ComboBox<>(map.keySet().toArray(new String[0]));
        comboBox.setEditable(edit);
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value != null) {
                    setText(map.get(String.valueOf(value)));
                }

                return this;
            }
        });

        comboBox.addActionListener(e -> {
            String selectedKey = (String)comboBox.getSelectedItem();
            PropertiesComponent.getInstance().setValue(filed, selectedKey);
        });

        return comboBox;
    }

    public static JLabel link(String title, String url) {
        JLabel jLabel = new JLabel("<html><a href=''>" + title + "</a></html>");
        jLabel.setForeground(new JBColor(new Color(87, 157, 246), new Color(87, 157, 246)));
        jLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        jLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ex) {
                    // 处理异常
                }
            }
        });

        return jLabel;
    }

    public static JPanel jPanel(ComboBox<String> comboBoxField, String labelName) {
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.setOpaque(false);
        JBLabel jbLabel = new JBLabel(labelName);
        jbLabel.setPreferredSize(new Dimension(100, 30));
        jPanel.add(jbLabel, BorderLayout.WEST);
        jPanel.add(comboBoxField, BorderLayout.CENTER);

        return jPanel;
    }

    public static JPanel jPanel(JBTextField textField, String labelName) {
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.setOpaque(false);
        JBLabel jbLabel = new JBLabel(labelName);
        jbLabel.setPreferredSize(new Dimension(100, 30));
        jPanel.add(jbLabel, BorderLayout.WEST);
        jPanel.add(textField, BorderLayout.CENTER);

        return jPanel;
    }

    public static JLabel jLabel(String labelName) {
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
            selectList.setSelectedItem("0");
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
