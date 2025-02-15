package com.sohocn.deep.seek.coder.sidebar;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.sohocn.deep.seek.coder.constant.AppConstant;

public class DeepSeekToolWindowFactory implements ToolWindowFactory {
    DeepSeekToolWindow deepSeekToolWindow;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        deepSeekToolWindow = new DeepSeekToolWindow(project);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(deepSeekToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);

        createTitleBarActions(project, toolWindow);
    }

    private void createTitleBarActions(Project project, ToolWindow toolWindow) {
        AnAction button1 = new AnAction("Configure DeepSeek API Key", "", AllIcons.Actions.ListFiles) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, AppConstant.PLUGIN_NAME);
            }
        };

        AnAction button2 = new AnAction("Clear Chat History", "", AllIcons.Actions.GC) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                int result = Messages
                    .showYesNoDialog("Are you sure you want to clear all chat history?", "Clear Confirmation",
                        Messages.getQuestionIcon());
                if (result == Messages.YES) {
                    deepSeekToolWindow.clearChatHistory();
                }
            }
        };

        // 将按钮添加到工具窗口标题栏
        toolWindow.setTitleActions(List.of(button1, button2));
    }
}