package com.sohocn.deep.seek.settings;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

import javax.swing.*;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.JBUI;

public class DeepSeekSettingsComponent {
    private final JPanel mainPanel;
    private final JBTextField apiKeyField;
    private final JTextArea roleDescriptionArea;
    private String originalApiKey;
    private String originalRoleDescription;
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";
    private static final String PROMPT = "com.sohocn.deepseek.prompt";

    public DeepSeekSettingsComponent() {
        // 初始化组件
        apiKeyField = new JBTextField();
        roleDescriptionArea = new JTextArea();

        // API Key 设置
        apiKeyField.setPreferredSize(new Dimension(500, 30));

        // 创建 API Key 链接标签
        JLabel apiKeyLink = new JLabel("<html><a href=''>Click here to apply for an API KEY</a></html>");
        apiKeyLink.setForeground(new Color(87, 157, 246));
        apiKeyLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        apiKeyLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://www.deepseek.com?from=DeepSeekCoder"));
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
        roleDescriptionArea.setLineWrap(true);
        roleDescriptionArea.setWrapStyleWord(true);
        roleDescriptionArea.setRows(5);
        roleDescriptionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        roleDescriptionArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JScrollPane scrollPane = new JScrollPane(roleDescriptionArea);
        scrollPane.setPreferredSize(new Dimension(500, 150));
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        // 创建标签面板，确保左对齐
        JPanel apiKeyPanel = new JPanel(new BorderLayout());
        apiKeyPanel.setOpaque(false);
        JBLabel apiKeyLabel = new JBLabel("API Key:");
        apiKeyLabel.setPreferredSize(new Dimension(100, 30));
        apiKeyPanel.add(apiKeyLabel, BorderLayout.WEST);
        apiKeyPanel.add(apiKeyField, BorderLayout.CENTER);

        // 修改角色描述面板布局
        JPanel roleDescPanel = new JPanel(new BorderLayout(5, 0)); // 添加水平间距
        roleDescPanel.setOpaque(false);

        // 创建标签包装面板，使用 FlowLayout(LEFT) 实现顶部对齐
        JPanel labelWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        labelWrapper.setOpaque(false);
        JBLabel roleDescLabel = new JBLabel("提示词:");
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
        gbc.insets = JBUI.insets(10, 0, 0, 0);
        mainPanel.add(new JSeparator(), gbc);

        gbc.gridy = 3;
        gbc.insets = JBUI.insets(10, 0, 0, 0);
        mainPanel.add(roleDescPanel, gbc);

        gbc.gridy = 4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(new JPanel(), gbc); // 填充剩余空间

        // 加载保存的设置
        loadSettings();
    }

    private void loadSettings() {
        DeepSeekSettingsState settings = ApplicationManager.getApplication().getService(DeepSeekSettingsState.class);
        originalApiKey = PropertiesComponent.getInstance().getValue(API_KEY, "");
        originalRoleDescription = settings.roleDescription;

        apiKeyField.setText(originalApiKey);
        roleDescriptionArea.setText(originalRoleDescription);
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public String getApiKey() {
        return apiKeyField.getText().trim();
    }

    public String getRoleDescription() {
        return roleDescriptionArea.getText().trim();
    }

    public boolean isModified() {
        return !getApiKey().equals(originalApiKey) || !getRoleDescription().equals(originalRoleDescription);
    }

    public void apply() {
        originalApiKey = getApiKey();
        originalRoleDescription = getRoleDescription();

        PropertiesComponent.getInstance().setValue(API_KEY, originalApiKey);
        PropertiesComponent.getInstance().setValue(PROMPT, originalRoleDescription);

        DeepSeekSettingsState settings = ApplicationManager.getApplication().getService(DeepSeekSettingsState.class);
        settings.roleDescription = originalRoleDescription;

        MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
        messageBus
            .syncPublisher(ApiKeyChangeNotifier.TOPIC)
            .apiKeyChanged(new ApiKeyChangeEvent(originalApiKey));
    }

    public void reset() {
        apiKeyField.setText(originalApiKey);
        roleDescriptionArea.setText(originalRoleDescription);
    }
} 