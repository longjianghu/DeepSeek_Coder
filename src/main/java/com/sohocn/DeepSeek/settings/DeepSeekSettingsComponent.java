package com.sohocn.DeepSeek.settings;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;

public class DeepSeekSettingsComponent {
    private final JPanel myMainPanel;
    private final JBTextField apiKeyField = new JBTextField();
    private String originalApiKey;
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";

    public DeepSeekSettingsComponent() {
        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent("API Key:", apiKeyField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
                
        originalApiKey = PropertiesComponent.getInstance().getValue(API_KEY, "");
        apiKeyField.setText(originalApiKey);
    }

    public JPanel getPanel() {
        return myMainPanel;
    }

    public String getApiKey() {
        return apiKeyField.getText().trim();
    }

    public void setApiKey(String apiKey) {
        apiKeyField.setText(apiKey);
        originalApiKey = apiKey;
    }

    public boolean isModified() {
        return !getApiKey().equals(originalApiKey);
    }

    public void apply() {
        originalApiKey = getApiKey();
        PropertiesComponent.getInstance().setValue(API_KEY, originalApiKey);
        // 使用应用级别的消息总线
        MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
        messageBus.syncPublisher(ApiKeyChangeNotifier.TOPIC).apiKeyChanged(new ApiKeyChangeEvent(originalApiKey));
    }

    public void reset() {
        apiKeyField.setText(originalApiKey);
    }
} 