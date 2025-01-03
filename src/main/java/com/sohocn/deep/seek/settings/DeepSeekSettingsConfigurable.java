package com.sohocn.deep.seek.settings;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.options.Configurable;

public class DeepSeekSettingsConfigurable implements Configurable {
    private DeepSeekSettingsComponent mySettingsComponent;

    @Override
    public String getDisplayName() {
        return "DeepSeek";
    }

    @Override
    public @Nullable JComponent createComponent() {
        mySettingsComponent = new DeepSeekSettingsComponent();
        return mySettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        return mySettingsComponent != null && mySettingsComponent.isModified();
    }

    @Override
    public void apply() {
        if (mySettingsComponent != null) {
            mySettingsComponent.apply();
        }
    }

    @Override
    public void reset() {
        if (mySettingsComponent != null) {
            mySettingsComponent.reset();
        }
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }
} 