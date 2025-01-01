package com.sohocn.DeepSeek.settings;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;

public class DeepSeekSettingsComponent {
    private final JPanel myMainPanel;
    private final JBTextField apiKeyField = new JBTextField();
    private final JSlider historyLimitSlider = new JSlider(JSlider.HORIZONTAL, 0, 50, 10);
    private final JLabel historyLimitLabel = new JLabel("10");
    private String originalApiKey;
    private int originalHistoryLimit;
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";
    private static final String HISTORY_LIMIT = "com.sohocn.deepseek.historyLimit";

    public DeepSeekSettingsComponent() {
        // 创建 API Key 链接标签
        JLabel apiKeyLink = new JLabel("<html><a href=''>点击这里申请API KEY</a></html>");
        apiKeyLink.setForeground(new Color(87, 157, 246));
        apiKeyLink.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 添加点击事件
        apiKeyLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://www.deepseek.com/"));
                } catch (Exception ex) {
                    // 处理异常
                }
            }
        });

        // 创建一个面板来包含链接，使用和输入框相同的缩进
        JPanel linkPanel = new JPanel(new BorderLayout());
        linkPanel.setOpaque(false);
        linkPanel.setBorder(JBUI.Borders.empty(0, 0, 5, 0));
        linkPanel.add(apiKeyLink, BorderLayout.WEST);

        // 配置历史记录数量滑块
        historyLimitSlider.setMajorTickSpacing(5);
        historyLimitSlider.setMinorTickSpacing(1);
        historyLimitSlider.setPaintTicks(true);
        historyLimitSlider.setPaintLabels(true);
        historyLimitSlider.setPreferredSize(new Dimension(300, 40));
        historyLimitSlider.addChangeListener(e -> 
            historyLimitLabel.setText(String.valueOf(historyLimitSlider.getValue())));

        // 创建历史记录设置面板
        JPanel historyPanel = new JPanel(new BorderLayout(10, 0));
        historyPanel.setOpaque(false);
        
        // 将标签放在滑块右侧
        JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        labelPanel.setOpaque(false);
        labelPanel.setPreferredSize(new Dimension(60, 30));
        labelPanel.add(historyLimitLabel);
        labelPanel.add(new JLabel("条"));
        
        historyPanel.add(historyLimitSlider, BorderLayout.CENTER);
        historyPanel.add(labelPanel, BorderLayout.EAST);

        // 使用 FormBuilder 构建表单，确保链接和输入框左对齐
        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent("API Key:", apiKeyField)
            .addComponentToRightColumn(linkPanel)
            .addLabeledComponent("历史记录:", historyPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
                
        // 加载保存的设置
        originalApiKey = PropertiesComponent.getInstance().getValue(API_KEY, "");
        originalHistoryLimit = PropertiesComponent.getInstance().getInt(HISTORY_LIMIT, 10);
        apiKeyField.setText(originalApiKey);
        historyLimitLabel.setText(String.valueOf(originalHistoryLimit));
    }

    public JPanel getPanel() {
        return myMainPanel;
    }

    public String getApiKey() {
        return apiKeyField.getText().trim();
    }

    public int getHistoryLimit() {
        try {
            return Integer.parseInt(historyLimitLabel.getText().trim());
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public void setApiKey(String apiKey) {
        apiKeyField.setText(apiKey);
        originalApiKey = apiKey;
    }

    public void setHistoryLimit(int limit) {
        historyLimitSlider.setValue(limit);
        originalHistoryLimit = limit;
    }

    public boolean isModified() {
        return !getApiKey().equals(originalApiKey) || getHistoryLimit() != originalHistoryLimit;
    }

    public void apply() {
        originalApiKey = getApiKey();
        originalHistoryLimit = getHistoryLimit();
        PropertiesComponent.getInstance().setValue(API_KEY, originalApiKey);
        PropertiesComponent.getInstance().setValue(HISTORY_LIMIT, originalHistoryLimit, 10);

        // 发布设置变更事件
        MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
        messageBus
            .syncPublisher(ApiKeyChangeNotifier.TOPIC)
            .apiKeyChanged(new ApiKeyChangeEvent(originalApiKey, originalHistoryLimit));
    }

    public void reset() {
        apiKeyField.setText(originalApiKey);
        historyLimitSlider.setValue(originalHistoryLimit);
    }
} 