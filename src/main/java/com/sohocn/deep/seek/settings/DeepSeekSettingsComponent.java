package com.sohocn.deep.seek.settings;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.JBUI;
import com.sohocn.deep.seek.constant.AppConstant;

public class DeepSeekSettingsComponent {
    private final JPanel mainPanel;
    private final JBTextField apiKeyField;
    private final ComboBox<String> modelField;
    private final JTextArea promptField;
    private final PropertiesComponent instance = PropertiesComponent.getInstance();
    private final DeepSeekSettingsState deepSeekSettingsState = new DeepSeekSettingsState();

    private String apiKey;
    private String prompt;
    private String model;

    public DeepSeekSettingsComponent() {
        Map<String, String> options = new HashMap<>();
        options.put("deepseek-chat", "DeepSeek-V3");
        options.put("deepseek-reasoner", "DeepSeek-R1");

        // 初始化组件
        apiKeyField = new JBTextField();
        modelField = new ComboBox<>(options.keySet().toArray(new String[0]));
        promptField = new JTextArea();

        modelField.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    setText(options.get(value));
                }
                return this;
            }
        });

        // 初始化默认值
        modelField.addActionListener(e -> {
            String selectedKey = (String)modelField.getSelectedItem();
            instance.setValue(AppConstant.MODEL, selectedKey);
        });

        if (instance.getValue(AppConstant.MODEL) == null) {
            instance.setValue(AppConstant.MODEL, deepSeekSettingsState.model);
        }

        if (instance.getValue(AppConstant.PROMPT) == null) {
            instance.setValue(AppConstant.PROMPT, deepSeekSettingsState.prompt);
        }

        // API Key 设置
        apiKeyField.setPreferredSize(new Dimension(500, 30));
        modelField.setPreferredSize(new Dimension(500, 30));

        // 创建 API Key 链接标签
        JLabel apiKeyLink = new JLabel("<html><a href=''>Click here to apply for an API KEY</a></html>");
        apiKeyLink.setForeground(new JBColor(new Color(87, 157, 246), new Color(87, 157, 246)));
        apiKeyLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        apiKeyLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI(AppConstant.APPLY_URL));
                } catch (Exception ex) {
                    // 处理异常
                }
            }
        });

        // 创建链接面板
        JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        linkPanel.setOpaque(false);
        linkPanel.add(apiKeyLink);

        // 角色描述设置
        promptField.setLineWrap(true);
        promptField.setWrapStyleWord(true);
        promptField.setRows(5);
        promptField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        promptField.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JScrollPane scrollPane = new JScrollPane(promptField);
        scrollPane.setPreferredSize(new Dimension(500, 150));
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        // 创建标签面板，确保左对齐
        JPanel apiKeyPanel = new JPanel(new BorderLayout());
        apiKeyPanel.setOpaque(false);
        JBLabel apiKeyLabel = new JBLabel("Api key:");
        apiKeyLabel.setPreferredSize(new Dimension(100, 30));
        apiKeyPanel.add(apiKeyLabel, BorderLayout.WEST);
        apiKeyPanel.add(apiKeyField, BorderLayout.CENTER);

        JPanel modelPanel = new JPanel(new BorderLayout());
        modelPanel.setOpaque(false);
        JBLabel modelLabel = new JBLabel("Chat model:");
        modelLabel.setPreferredSize(new Dimension(100, 30));
        modelPanel.add(modelLabel, BorderLayout.WEST);
        modelPanel.add(modelField, BorderLayout.CENTER);

        // 修改角色描述面板布局
        JPanel roleDescPanel = new JPanel(new BorderLayout(5, 0)); // 添加水平间距
        roleDescPanel.setOpaque(false);

        // 创建标签包装面板，使用 FlowLayout(LEFT) 实现顶部对齐
        JPanel labelWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        labelWrapper.setOpaque(false);
        JBLabel roleDescLabel = new JBLabel("Prompt:");
        roleDescLabel.setPreferredSize(new Dimension(100, 20)); // 减小高度
        labelWrapper.add(roleDescLabel);

        roleDescPanel.add(labelWrapper, BorderLayout.WEST);
        roleDescPanel.add(scrollPane, BorderLayout.CENTER);

        // 构建主面板
        mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(apiKeyPanel, gbc);

        gbc.gridy = 1;
        gbc.insets = JBUI.insets(0, 100, 5, 0); // 左边距与 API Key 输入框对齐
        mainPanel.add(linkPanel, gbc);

        gbc.gridy = 2;
        gbc.insets = JBUI.insetsTop(10); // 移除左边距，与 API Key 面板对齐
        mainPanel.add(modelPanel, gbc);

        gbc.gridy = 3;
        gbc.insets = JBUI.insetsTop(10);
        mainPanel.add(roleDescPanel, gbc);

        gbc.gridy = 6;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(new JPanel(), gbc); // 填充剩余空间

        // 加载保存的设置
        loadSettings();
    }

    private void loadSettings() {
        // 直接从 PropertiesComponent 获取所有配置
        apiKey = instance.getValue(AppConstant.API_KEY, "");
        model = instance.getValue(AppConstant.MODEL, deepSeekSettingsState.model); // 提供默认值
        prompt = instance.getValue(AppConstant.PROMPT, deepSeekSettingsState.prompt); // 提供默认值

        apiKeyField.setText(apiKey);
        modelField.setSelectedItem(model);
        promptField.setText(prompt);
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public String getApiKey() {
        return apiKeyField.getText().trim();
    }

    public String getModel() {
        return (String)modelField.getSelectedItem();
    }

    public String getPrompt() {
        return promptField.getText().trim();
    }

    public boolean isModified() {
        String currentApiKey = getApiKey();
        String currentPrompt = getPrompt();
        String currentModel = getModel();

        return !currentApiKey.equals(apiKey) || !currentPrompt.equals(prompt) || !currentModel.equals(model);
    }

    public void apply() {
        apiKey = getApiKey();
        model = getModel();
        prompt = getPrompt();

        // 直接保存到 PropertiesComponent
        instance.setValue(AppConstant.API_KEY, apiKey);
        instance.setValue(AppConstant.MODEL, model);
        instance.setValue(AppConstant.PROMPT, prompt);

        // 发送通知
        MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
        messageBus.syncPublisher(ApiKeyChangeNotifier.TOPIC).apiKeyChanged(new ApiKeyChangeEvent(apiKey));
    }

    public void reset() {
        apiKeyField.setText(apiKey);
        modelField.setSelectedItem(model);
        promptField.setText(prompt);
    }
}