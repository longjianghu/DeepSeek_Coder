package com.sohocn.deep.seek.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
    name = "com.sohocn.deep.seek.settings.DeepSeekSettingsState",
    storages = @Storage("DeepSeekSettings.xml")
)
public class DeepSeekSettingsState implements PersistentStateComponent<DeepSeekSettingsState> {
    public String model = "deepseek-chat";
    public String prompt =
        "You are a helpful assistant specialized in programming and software development.Your task is to assist users with questions related to coding, debugging, software design, algorithms, and other programming-related topics. If a user asks a question outside of these areas, politely inform them that you are only able to assist with programming-related queries.";

    @Nullable
    @Override
    public DeepSeekSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull DeepSeekSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
} 