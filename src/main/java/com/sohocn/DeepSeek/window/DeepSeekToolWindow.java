package com.sohocn.DeepSeek.window;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBLabel;
import com.sohocn.DeepSeek.settings.ApiKeyChangeNotifier;
import com.sohocn.DeepSeek.settings.ApiKeyChangeEvent;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DeepSeekToolWindow {
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";
    private final JPanel content;
    private final JBTextArea outputArea;
    private final JBTextArea inputArea;
    private final Project project;
    private JBLabel configLabel;  // 添加成员变量以便后续控制显示/隐藏

    public DeepSeekToolWindow(Project project) {
        this.project = project;
        content = new JPanel(new BorderLayout());
        
        // 输出区域
        outputArea = new JBTextArea();
        outputArea.setEditable(false);
        JBScrollPane outputScrollPane = new JBScrollPane(outputArea);
        
        // 输入区域
        inputArea = new JBTextArea();
        inputArea.setRows(5);
        int lineHeight = inputArea.getFont().getSize() + 2;
        inputArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20 * lineHeight));
        JBScrollPane inputScrollPane = new JBScrollPane(inputArea);

        // 创建一个面板来包含configLabel，使其顶部对齐
        JPanel topPanel = new JPanel(new BorderLayout());
        configLabel = createConfigLabel();
        topPanel.add(configLabel, BorderLayout.NORTH);
        
        content.add(topPanel, BorderLayout.NORTH);  // 将提示文本放在顶部
        content.add(outputScrollPane, BorderLayout.CENTER);
        content.add(inputScrollPane, BorderLayout.SOUTH);

        // 使用应用级别的消息总线
        ApplicationManager.getApplication().getMessageBus().connect()
            .subscribe(ApiKeyChangeNotifier.TOPIC, event -> {
                SwingUtilities.invokeLater(this::checkApiKeyConfig);
            });

        checkApiKeyConfig();
    }

    private JBLabel createConfigLabel() {
        JBLabel label = new JBLabel("<html><u>你还没有配置DeepSeek的API KEY,请点击这里配置！</u></html>");
        label.setForeground(Color.BLUE);
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));  // 添加一些内边距
        
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "DeepSeek");
            }
        });
        
        return label;
    }

    private void checkApiKeyConfig() {
        String apiKey = PropertiesComponent.getInstance().getValue(API_KEY);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            // 如果没有配置API Key，显示提示文本，隐藏输出区域
            configLabel.setVisible(true);
            outputArea.setVisible(false);
        } else {
            // 如果已配置API Key，隐藏提示文本，显示输出区域
            configLabel.setVisible(false);
            outputArea.setVisible(true);
            outputArea.setText("");  // 或者可以显示欢迎信息
        }
    }

    public void refresh() {
        // 提供刷新方法，用于配置更新后刷新显示
        checkApiKeyConfig();
    }

    public JPanel getContent() {
        return content;
    }
} 