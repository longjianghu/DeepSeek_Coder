package com.sohocn.deep.seek.startup;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.sohocn.deep.seek.constant.AppConstant;
import com.sohocn.deep.seek.settings.DeepSeekSettingsState;
import org.jetbrains.annotations.NotNull;

public class DeepSeekStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        // 初始化默认配置
        DeepSeekSettingsState defaultSettings = new DeepSeekSettingsState();
        PropertiesComponent properties = PropertiesComponent.getInstance();

        // 如果配置不存在，设置默认值
        if (properties.getValue(AppConstant.MODEL) == null) {
            properties.setValue(AppConstant.MODEL, defaultSettings.model);
        }
        if (properties.getValue(AppConstant.PROMPT) == null) {
            properties.setValue(AppConstant.PROMPT, defaultSettings.prompt);
        }
    }
} 