package com.sohocn.deep.seek.coder.settings;

import java.awt.*;
import java.util.Map;

import javax.swing.*;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.JBUI;
import com.sohocn.deep.seek.coder.config.PlatformConfig;
import com.sohocn.deep.seek.coder.constant.AppConstant;
import com.sohocn.deep.seek.coder.event.ChangeEvent;
import com.sohocn.deep.seek.coder.event.ChangeNotifier;
import com.sohocn.deep.seek.coder.util.LayoutUtil;

public class DeepSeekSettingsComponent {
    private final JPanel mainPanel;
    private final JBTextField apiKeyField = new JBTextField();
    private final ComboBox<String> platformField;
    private final ComboBox<String> modelField;
    private final JTextArea promptField;
    private final PropertiesComponent instance = PropertiesComponent.getInstance();

    private String apiKey;
    private String platform;
    private String prompt;
    private String model;

    public DeepSeekSettingsComponent() {
        PlatformConfig platformConfig = new PlatformConfig();

        Map<String, String> platformMap = platformConfig.platformMap();
        Map<String, String> deepSeekModelMap = platformConfig.deepSeekModelMap();

        platformField = LayoutUtil.comboBox(platformMap, AppConstant.PLATFORM, false);
        modelField = LayoutUtil.comboBox(deepSeekModelMap, AppConstant.MODEL, false);
        promptField = LayoutUtil.jTextArea();

        // 设置初始值
        platformField.setSelectedItem(platform);
        modelField.setSelectedItem(model);
        promptField.setText(prompt);
        apiKeyField.setText(apiKey);

        // 创建链接面板
        JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        linkPanel.setOpaque(false);

        JLabel deepSeek =
            LayoutUtil.link(platformMap.get(AppConstant.DEEP_SEEK), platformConfig.applyUrlMap(AppConstant.DEEP_SEEK));
        deepSeek.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));

        JLabel siliconFlow = LayoutUtil
            .link(platformMap.get(AppConstant.SILICON_FLOW), platformConfig.applyUrlMap(AppConstant.SILICON_FLOW));

        linkPanel.add(deepSeek);
        linkPanel.add(siliconFlow);

        JScrollPane scrollPane = new JBScrollPane(promptField);
        scrollPane.setPreferredSize(new Dimension(500, 150));
        scrollPane.setBorder(BorderFactory.createLineBorder(LayoutUtil.borderColor()));

        // 修改角色描述面板布局
        JPanel roleDescPanel = new JPanel(new BorderLayout(5, 0));
        roleDescPanel.setOpaque(false);

        // 创建标签包装面板，使用 FlowLayout(LEFT) 实现顶部对齐
        JPanel labelWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        labelWrapper.setOpaque(false);
        roleDescPanel.add(labelWrapper, BorderLayout.WEST);

        JBLabel promptLabel = new JBLabel("Prompt:");
        promptLabel.setPreferredSize(new Dimension(100, 20));
        labelWrapper.add(promptLabel);
        roleDescPanel.add(scrollPane, BorderLayout.CENTER);

        // 主面板
        mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(JBUI.Borders.empty(10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(LayoutUtil.jPanel(platformField, "Platform:"), gbc);

        gbc.gridy = 1;
        gbc.insets = JBUI.insets(0, 100, 5, 0);
        mainPanel.add(linkPanel, gbc);

        gbc.gridy = 2;
        gbc.insets = JBUI.insetsTop(10);
        mainPanel.add(LayoutUtil.jPanel(apiKeyField, "Api Key:"), gbc);

        gbc.gridy = 3;
        gbc.insets = JBUI.insetsTop(10);
        mainPanel.add(LayoutUtil.jPanel(modelField, "Chat Model:"), gbc);

        gbc.gridy = 4;
        gbc.insets = JBUI.insetsTop(10);
        mainPanel.add(roleDescPanel, gbc);

        gbc.gridy = 5;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(new JPanel(), gbc);
    }

    private void loadSettings() {
        platform = instance.getValue(AppConstant.PLATFORM, AppConstant.DEFAULT_PLATFORM);
        model = instance.getValue(AppConstant.MODEL, AppConstant.DEFAULT_MODEL);
        prompt = instance.getValue(AppConstant.PROMPT, AppConstant.DEFAULT_PROMPT);
        apiKey = instance.getValue(AppConstant.API_KEY, "");
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public String getPlatform() {
        return (String)platformField.getSelectedItem();
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
        String currentPlatform = getPlatform();
        String currentApiKey = getApiKey();
        String currentPrompt = getPrompt();
        String currentModel = getModel();

        return !currentPlatform.equals(platform) || !currentApiKey.equals(apiKey) || !currentPrompt.equals(prompt)
            || !currentModel.equals(model);
    }

    public void apply() {
        platform = getPlatform();
        apiKey = getApiKey();
        model = getModel();
        prompt = getPrompt();

        // 直接保存到 PropertiesComponent
        instance.setValue(AppConstant.PLATFORM, platform);
        instance.setValue(AppConstant.API_KEY, apiKey);
        instance.setValue(AppConstant.MODEL, model);
        instance.setValue(AppConstant.PROMPT, prompt);

        // 发送通知
        MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
        messageBus.syncPublisher(ChangeNotifier.TOPIC).changed(new ChangeEvent(apiKey));
    }

    public void reset() {
        loadSettings();

        platformField.setSelectedItem(platform);
        apiKeyField.setText(apiKey);
        modelField.setSelectedItem(model);
        promptField.setText(prompt);
    }
}