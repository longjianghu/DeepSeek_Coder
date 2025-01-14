package com.sohocn.deep.seek.window;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.sohocn.deep.seek.constant.AppConstant;
import com.sohocn.deep.seek.service.DeepSeekService;
import com.sohocn.deep.seek.settings.ApiKeyChangeNotifier;
import com.sohocn.deep.seek.utils.MarkdownRenderer;

import groovyjarjarantlr4.v4.runtime.misc.NotNull;

public class DeepSeekToolWindow {
    private static final int MESSAGE_HORIZONTAL_MARGIN = 20; // 左右边距各20像素
    private static final Gson gson = new GsonBuilder().create();
    private final JPanel content;
    private final JBPanel<JBPanel<?>> chatPanel;
    private final JBTextArea inputArea = new JBTextArea();
    private final Project project;
    private final DeepSeekService deepSeekService;

    public DeepSeekToolWindow(Project project) {
        this.project = project;
        this.deepSeekService = new DeepSeekService();

        content = new JPanel(new BorderLayout());

        // 使用更深的背景色
        Color backgroundColor = new Color(30, 30, 30); // 使用固定的深色背景
        content.setBackground(backgroundColor);

        // 聊天区域
        chatPanel = new JBPanel<>(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0));
        chatPanel.setBackground(backgroundColor);
        JBScrollPane chatScrollPane = new JBScrollPane(chatPanel);
        chatScrollPane.setBackground(backgroundColor);
        chatScrollPane.setBorder(JBUI.Borders.empty());
        chatScrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        // 初始化输入区域
        initializeInputArea();

        // 顶部工具栏
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(backgroundColor);
        topPanel.setBorder(JBUI.Borders.empty(5, 10)); // 添加内边距

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0)); // 增加图标间距
        rightPanel.setOpaque(false);

        // 设置按钮
        JLabel settingsLabel = createToolbarButton("⚙️", "Setting");
        settingsLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "DeepSeek");
            }
        });

        JLabel clearHistoryLabel = createToolbarButton("🗑️", "Clear History");
        clearHistoryLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int result = Messages
                    .showYesNoDialog("Are you sure you want to clear all chat history?", "Clear Confirmation",
                        Messages.getQuestionIcon());
                if (result == Messages.YES) {
                    chatPanel.removeAll();
                    chatPanel.revalidate();
                    chatPanel.repaint();
                    // 清除保存的历史记录
                    PropertiesComponent.getInstance().unsetValue(AppConstant.CHAT_HISTORY);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                clearHistoryLabel.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearHistoryLabel.setForeground(new Color(153, 153, 153));
            }
        });

        rightPanel.add(settingsLabel);
        rightPanel.add(clearHistoryLabel);
        topPanel.add(rightPanel, BorderLayout.EAST);

        // 添加回车发送功能
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                }
            }
        });

        // 修改输入框的提示文本
        inputArea.putClientProperty("StatusVisibleFunction", (Supplier<Boolean>)() -> true);
        checkApiKeyConfig(); // 这个方法会设置适当的提示文本

        content.add(topPanel, BorderLayout.NORTH);
        content.add(chatScrollPane, BorderLayout.CENTER);

        // 订阅设置变更事件
        ApplicationManager.getApplication().getMessageBus().connect().subscribe(ApiKeyChangeNotifier.TOPIC, event -> {
            SwingUtilities.invokeLater(this::checkApiKeyConfig);
        });

        addComponentListener(); // 添加大小变化监听

        // 订阅项目关闭事件
        project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                saveChatHistory();
            }
        });

        // 确保在所有组件初始化完成后加载历史记录
        ApplicationManager.getApplication().invokeLater(() -> {
            loadChatHistory();
            checkApiKeyConfig();
        });

        // 添加窗口激活监听器
        project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            @Override
            public void toolWindowShown(@NotNull ToolWindow toolWindow) {
                if ("DeepSeek".equals(toolWindow.getId())) {
                    loadChatHistory();
                }
            }
        });
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane)chatPanel.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum() - vertical.getVisibleAmount());
        });
    }

    private void sendMessage() {
        // 获取输入内容并进行前后空格过滤
        String message = inputArea.getText().replaceAll("^\\s+|\\s+$", "");

        if (!message.isEmpty()) {
            // 禁用输入框
            inputArea.setEnabled(false);
            inputArea.setText("");

            // 确保在 EDT 线程中添加消息
            SwingUtilities.invokeLater(() -> {
                // 显示用户消息
                JBPanel<JBPanel<?>> userBubble = createMessageBubble(message, true);
                userBubble.putClientProperty("originalMessage", message);
                chatPanel.add(userBubble);

                // 创建 AI 回复的气泡
                JBPanel<JBPanel<?>> aiBubble = createMessageBubble("", false);
                chatPanel.add(aiBubble);
                chatPanel.revalidate();
                chatPanel.repaint();

                // 立即滚动到底部
                scrollToBottom();

                // 在后台线程中发送请求
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        StringBuilder fullResponse = new StringBuilder();

                        deepSeekService.streamMessage(message, chunk -> SwingUtilities.invokeLater(() -> {
                            fullResponse.append(chunk);
                            String currentResponse = fullResponse.toString();
                            aiBubble.putClientProperty("originalMessage", currentResponse);

                            // 更新消息内容
                            JEditorPane textArea = (JEditorPane)aiBubble.getClientProperty("textArea");
                            textArea.setText(MarkdownRenderer.renderMarkdown(currentResponse));

                            // 调整大小，考虑侧边栏宽度
                            int maxWidth = chatPanel.getWidth() - (MESSAGE_HORIZONTAL_MARGIN * 2);
                            adjustMessageSize(aiBubble, maxWidth);

                            // 重新布局
                            aiBubble.revalidate();
                            chatPanel.revalidate();
                        }),
                            // 忽略 token 信息
                            () -> SwingUtilities.invokeLater(() -> {
                                inputArea.setEnabled(true);
                                inputArea.requestFocus();
                                scrollToBottom();
                            }));
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            chatPanel.remove(aiBubble);
                            addMessageBubble("Error: " + e.getMessage(), false);
                            inputArea.setEnabled(true);
                            inputArea.requestFocus();
                        });
                    }
                });
            });
        }
    }

    private void addMessageBubble(String message, boolean isUser) {
        JBPanel<JBPanel<?>> bubble = (JBPanel<JBPanel<?>>)createMessageBubble(message, isUser);
        chatPanel.add(bubble);

        // 检查是否超过历史记录限制
        int historyLimit = PropertiesComponent.getInstance().getInt(AppConstant.HISTORY_LIMIT, 10);
        int maxMessages = historyLimit * 2; // *2 因为每次对话包含用户消息和AI回复

        // 如果超过限制，从头开始删除多余的消息
        while (chatPanel.getComponentCount() > maxMessages) {
            chatPanel.remove(0);
        }

        chatPanel.revalidate();
        chatPanel.repaint();
        smoothScrollToBottom();
    }

    private JBPanel<JBPanel<?>> createMessageBubble(String message, boolean isUser) {
        JBPanel<JBPanel<?>> bubble = new JBPanel<>(new BorderLayout());
        bubble.setBackground(isUser ? new Color(40, 40, 40) : new Color(35, 35, 35));
        bubble.setBorder(JBUI.Borders.empty(10));

        // 创建消息文本区域
        JEditorPane textArea = new JEditorPane();
        textArea.setEditorKit(JEditorPane.createEditorKitForContentType("text/html"));
        textArea.setEditable(false);
        textArea.setBackground(bubble.getBackground());
        textArea.setBorder(null);
        textArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        // 创建一个面板来包装文本区域
        JBPanel<JBPanel<?>> textPanel = new JBPanel<>(new BorderLayout());
        textPanel.setBackground(bubble.getBackground());
        textPanel.add(textArea, BorderLayout.CENTER);

        // 渲染消息内容
        if (isUser) {
            // 用户消息使用简单的 HTML 包装
            String escapedMessage = message.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
            String userContent = String
                .format("<body style='margin:0;padding:8px;" + "background-color:#2B2B2B;border:1px solid #646464;"
                    + "border-radius:5px;color:#DCDCDC;white-space:pre-wrap;'>" + "%s</body>", escapedMessage);
            textArea.setText(MarkdownRenderer.renderHtml(userContent));
        } else {
            // AI 消息使用 Markdown 渲染
            textArea.setText(MarkdownRenderer.renderMarkdown(message));
        }

        // 存储原始消息和文本区域
        bubble.putClientProperty("originalMessage", message);
        bubble.putClientProperty("textArea", textArea);
        bubble.putClientProperty("textPanel", textPanel);

        // 添加到气泡
        bubble.add(textPanel, BorderLayout.CENTER);

        return bubble;
    }

    private void adjustMessageSize(JBPanel<JBPanel<?>> bubble, int maxWidth) {
        if (maxWidth <= 0)
            return;

        JEditorPane textArea = (JEditorPane)bubble.getClientProperty("textArea");
        JBPanel<?> textPanel = (JBPanel<?>)bubble.getClientProperty("textPanel");

        // 计算实际可用宽度，考虑边距和滚动条
        int availableWidth = Math.min(maxWidth, chatPanel.getParent().getWidth() - 40); // 使用滚动面板的宽度

        // 设置最大宽度并计算首选高度
        textArea.setSize(availableWidth, Short.MAX_VALUE);
        int preferredHeight = textArea.getPreferredSize().height;

        // 设置面板大小，添加一些垂直内边距
        textPanel.setPreferredSize(new Dimension(availableWidth, preferredHeight + 10));

        bubble.revalidate();
    }

    private void checkApiKeyConfig() {
        String apiKey = PropertiesComponent.getInstance().getValue(AppConstant.API_KEY);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            inputArea.setEnabled(false);
        } else {
            inputArea.setEnabled(true);
        }
    }

    // 添加组件大小变化监听
    private void addComponentListener() {
        content.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // 当窗口大小改变时，重新调整所有消息气泡的大小
                int maxWidth = chatPanel.getWidth() - (MESSAGE_HORIZONTAL_MARGIN * 2);

                for (Component component : chatPanel.getComponents()) {
                    if (component instanceof JBPanel) {
                        adjustMessageSize((JBPanel<JBPanel<?>>)component, maxWidth);
                    }
                }
                chatPanel.revalidate();
            }
        });
    }

    // 保存聊天记录
    private void saveChatHistory() {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            Component[] components = chatPanel.getComponents();

            for (int i = 0; i < components.length; i++) {
                if (components[i] instanceof JBPanel) {
                    JBPanel<?> bubble = (JBPanel<?>)components[i];
                    String message = (String)bubble.getClientProperty("originalMessage");

                    // 通过判断消息的位置来确定是否为用户消息
                    // 用户消息和 AI 回复总是成对出现，用户消息在偶数位置
                    boolean isUser = (i % 2 == 0);

                    if (message != null && !message.isEmpty()) {
                        messages.add(new ChatMessage(message, isUser));
                    }
                }
            }

            // 限制保存的消息数量
            int historyLimit = PropertiesComponent.getInstance().getInt(AppConstant.HISTORY_LIMIT, 10);
            if (messages.size() > historyLimit * 2) {
                messages = messages.subList(messages.size() - 10, messages.size());
            }

            if (!messages.isEmpty()) {
                String json = gson.toJson(messages);
                PropertiesComponent.getInstance().setValue(AppConstant.CHAT_HISTORY, json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 修改加载聊天记录方法
    private void loadChatHistory() {
        try {
            String json = PropertiesComponent.getInstance().getValue(AppConstant.CHAT_HISTORY);

            if (json != null && !json.isEmpty()) {
                Type listType = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
                List<ChatMessage> messages = gson.fromJson(json, listType);

                if (messages != null && !messages.isEmpty()) {
                    chatPanel.removeAll();

                    for (ChatMessage message : messages) {
                        // 创建消息气泡，传入正确的 isUser 参数
                        JBPanel<JBPanel<?>> bubble = createMessageBubble(message.getContent(), message.isUser());
                        bubble.putClientProperty("originalMessage", message.getContent());
                        chatPanel.add(bubble);
                    }

                    chatPanel.revalidate();
                    chatPanel.repaint();

                    // 使用 SwingUtilities.invokeLater 确保在 UI 更新后滚动
                    SwingUtilities.invokeLater(() -> {
                        // 等待一个短暂的时间，确保组件已经完全布局
                        Timer timer = new Timer(100, e -> {
                            smoothScrollToBottom();
                            ((Timer)e.getSource()).stop();
                        });
                        timer.setRepeats(false);
                        timer.start();
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 创建工具栏按钮
    private JLabel createToolbarButton(String icon, String tooltip) {
        JLabel button = new JLabel(icon);
        button.setToolTipText(tooltip);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setForeground(new Color(153, 153, 153));
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16)); // 调整图标大小

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(new Color(153, 153, 153));
            }
        });

        return button;
    }

    // 现代风格的滚动条 UI
    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(88, 88, 88);
            trackColor = new Color(43, 43, 43);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }

            Graphics2D g2 = (Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 8, 8);
            g2.dispose();
        }
    }

    public JPanel getContent() {
        return content;
    }

    // 修改输入区域的初始化代码
    private void initializeInputArea() {
        // 输入区域面板
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(new Color(30, 30, 30));
        inputPanel.setBorder(JBUI.Borders.empty(10));

        // 创建一个带边框的面板
        JPanel borderPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 使用更深的背景色
                g2.setColor(new Color(43, 43, 43));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                // 使用更明显的边框颜色
                g2.setColor(new Color(100, 100, 100));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

                g2.dispose();
            }

            @Override
            public boolean isOpaque() {
                return false;
            }
        };
        borderPanel.setBorder(JBUI.Borders.empty(1));

        // 输入框
        inputArea.setBackground(new Color(43, 43, 43));
        inputArea.setForeground(new Color(220, 220, 220));
        inputArea.setCaretColor(Color.WHITE);
        inputArea.setBorder(JBUI.Borders.empty(8, 8, 24, 8));
        inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setRows(3);
        inputArea.setOpaque(false);

        // 创建选择列表前的提示标签
        JLabel promptLabel = new JLabel("Context");
        promptLabel.setForeground(new Color(153, 153, 153));
        promptLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        promptLabel.setBorder(JBUI.Borders.empty(0, 8, 6, 8));

        // 创建选择列表
        String[] options = {"0", "1", "2", "3", "4", "5"};
        JComboBox<String> selectList = new JComboBox<>(options);
        selectList.setForeground(new Color(153, 153, 153));
        selectList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        selectList.setBorder(JBUI.Borders.empty(0, 8, 6, 8));

        selectList
            .addActionListener(e -> PropertiesComponent
                .getInstance()
                .setValue(AppConstant.OPTION_VALUE, selectList.getSelectedItem().toString()));

        // 快捷键提示标签
        JLabel hintLabel = new JLabel("Press Enter to submit, Shift Enter to complete the line");
        hintLabel.setForeground(new Color(153, 153, 153));
        hintLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        hintLabel.setBorder(JBUI.Borders.empty(0, 8, 6, 8));

        // 创建底部面板
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);

        // 创建一个包装面板，使用 FlowLayout(LEFT) 实现左对齐
        JPanel wrapperPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapperPanel.setOpaque(false);
        wrapperPanel.add(promptLabel);
        wrapperPanel.add(selectList);
        wrapperPanel.add(hintLabel);

        bottomPanel.add(wrapperPanel, BorderLayout.EAST);

        // 组装面板
        borderPanel.add(inputArea, BorderLayout.CENTER);
        borderPanel.add(bottomPanel, BorderLayout.SOUTH);
        inputPanel.add(borderPanel, BorderLayout.CENTER);

        // 添加回车发送功能
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        // Shift+回车换行
                        inputArea.append("\n");
                    } else {
                        // 回车发送
                        e.consume();
                        sendMessage();
                    }
                }
            }
        });

        content.add(inputPanel, BorderLayout.SOUTH);
    }

    // 修改平滑滚动方法
    private void smoothScrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollPane scrollPane = (JScrollPane)chatPanel.getParent().getParent();
            JScrollBar vertical = scrollPane.getVerticalScrollBar();

            // 获取目标位置（最大滚动值）
            int targetValue = vertical.getMaximum() - vertical.getVisibleAmount();
            int currentValue = vertical.getValue();

            // 如果已经在底部，不需要滚动
            if (currentValue >= targetValue) {
                return;
            }

            // 创建平滑滚动动画
            Timer timer = new Timer(16, null); // 使用 16ms 的间隔（约60fps）
            final int[] lastValue = {currentValue}; // 记录上一次的值

            timer.addActionListener(e -> {
                if (!vertical.getValueIsAdjusting()) { // 只在用户没有手动滚动时执行
                    int newValue = lastValue[0];
                    int remainingDistance = targetValue - newValue;
                    int step = remainingDistance / 6; // 使用更平滑的步长

                    // 确保最小步长
                    if (Math.abs(step) < 1) {
                        step = remainingDistance > 0 ? 1 : -1;
                    }

                    newValue += step;

                    // 检查是否到达目标
                    if ((step > 0 && newValue >= targetValue) || (step < 0 && newValue <= targetValue)
                        || Math.abs(remainingDistance) <= 1) {
                        vertical.setValue(targetValue);
                        timer.stop();
                    } else {
                        vertical.setValue(newValue);
                        lastValue[0] = newValue;
                    }
                }
            });

            // 添加鼠标滚轮监听器
            MouseWheelListener[] listeners = scrollPane.getMouseWheelListeners();
            for (MouseWheelListener listener : listeners) {
                scrollPane.removeMouseWheelListener(listener);
            }

            scrollPane.addMouseWheelListener(e -> {
                if (timer.isRunning()) {
                    timer.stop(); // 如果用户滚动，停止自动滚动
                }
                // 处理正常的滚轮事件
                int units = e.getUnitsToScroll();
                int delta = units * vertical.getUnitIncrement();
                vertical.setValue(vertical.getValue() + delta);
            });

            timer.start();
        });
    }
}