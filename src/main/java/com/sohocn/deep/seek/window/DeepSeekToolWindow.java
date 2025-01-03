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
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.sohocn.deep.seek.service.DeepSeekService;
import com.sohocn.deep.seek.settings.ApiKeyChangeNotifier;

import groovyjarjarantlr4.v4.runtime.misc.NotNull;

public class DeepSeekToolWindow {
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";
    private static final String CHAT_HISTORY = "com.sohocn.deepseek.chatHistory";
    private static final String HISTORY_LIMIT = "com.sohocn.deepseek.historyLimit";
    private static final int MESSAGE_TOTAL_MARGIN = 30; // 消息气泡的总边距
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
        chatPanel = new JBPanel<>(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
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
        JLabel settingsLabel = createToolbarButton("⚙️", "设置");
        settingsLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "DeepSeek");
            }
        });

        JLabel clearHistoryLabel = createToolbarButton("🗑️", "清除历史记录");
        clearHistoryLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int result = Messages.showYesNoDialog("确定要清除所有聊天记录吗？", "清除确认", Messages.getQuestionIcon());
                if (result == Messages.YES) {
                    chatPanel.removeAll();
                    chatPanel.revalidate();
                    chatPanel.repaint();
                    // 清除保存的历史记录
                    PropertiesComponent.getInstance().unsetValue(CHAT_HISTORY);
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
        ApplicationManager.getApplication().getMessageBus().connect()
            .subscribe(ApiKeyChangeNotifier.TOPIC, event -> {
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
            JScrollPane scrollPane = (JScrollPane) chatPanel.getParent().getParent();
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

                        deepSeekService.streamMessage(
                            message,
                            chunk -> SwingUtilities.invokeLater(() -> {
                                fullResponse.append(chunk);
                                String currentResponse = fullResponse.toString();
                                aiBubble.putClientProperty("originalMessage", currentResponse);

                                // 更新消息内容
                                JEditorPane textArea = (JEditorPane) aiBubble.getClientProperty("textArea");
                                textArea.setContentType("text/html");
                                textArea.setText(wrapContent(currentResponse, false));

                                // 调整大小
                                int maxWidth = Math.min(chatPanel.getWidth(), content.getWidth()) - (MESSAGE_HORIZONTAL_MARGIN * 2);
                                if (maxWidth > 0) {
                                    textArea.setSize(maxWidth, Short.MAX_VALUE);
                                    int preferredHeight = textArea.getPreferredSize().height;
                                    ((JPanel)aiBubble.getComponent(0)).setPreferredSize(
                                        new Dimension(maxWidth, preferredHeight + 10)
                                    );
                                }

                                // 重新布局
                                aiBubble.revalidate();
                                chatPanel.revalidate();
                            }),
                            // 忽略 token 信息
                            () -> SwingUtilities.invokeLater(() -> {
                                inputArea.setEnabled(true);
                                inputArea.requestFocus();
                                scrollToBottom();
                            })
                        );
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
        JBPanel<JBPanel<?>> bubble = createMessageBubble(message, isUser);
        chatPanel.add(bubble);

        // 检查是否超过历史记录限制
        int historyLimit = PropertiesComponent.getInstance().getInt(HISTORY_LIMIT, 10);
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
        bubble.setBackground(null);
        bubble.setBorder(JBUI.Borders.empty(0, MESSAGE_HORIZONTAL_MARGIN));

        // 创建文本区域
        JEditorPane textArea = new JEditorPane();
        textArea.setEditable(false);
        textArea.setBorder(JBUI.Borders.empty(2));
        textArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        // 创建一个带圆角边框的面板
        JPanel roundedPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.dispose();
            }
        };
        roundedPanel.setOpaque(false);

        // 统一设置文本内容的渲染逻辑
        textArea.setBackground(null);
        textArea.setForeground(new Color(220, 220, 220));
        
        // 所有消息都使用 HTML 渲染，以支持样式
        textArea.setContentType("text/html");
        textArea.setText(wrapContent(message, isUser));

        roundedPanel.add(textArea);
        bubble.add(roundedPanel, BorderLayout.WEST);

        // 统一设置大小，确保不超过侧边栏宽度
        int maxWidth = Math.min(chatPanel.getWidth(), content.getWidth()) - (MESSAGE_HORIZONTAL_MARGIN * 2);
        if (maxWidth > 0) {
            textArea.setSize(maxWidth, Short.MAX_VALUE);
            int preferredHeight = textArea.getPreferredSize().height;
            roundedPanel.setPreferredSize(new Dimension(maxWidth, preferredHeight + 10));
        }

        // 保存文本区域的引用
        bubble.putClientProperty("textArea", textArea);
        bubble.putClientProperty("originalMessage", message);

        return bubble;
    }

    private String wrapContent(String message, boolean isUser) {
        StringBuilder html = new StringBuilder();
        String[] parts = message.split("```");
        
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 0) {
                // 普通文本
                String text = parts[i].replace("<", "&lt;")
                                    .replace(">", "&gt;")
                                    .replace("\n", "<br>")
                                    .replace(" ", "&nbsp;");
                if (isUser) {
                    // 用户消息使用带边框的样式
                    html.append("<div class='user-message'>").append(text).append("</div>");
                } else {
                    // AI 回复直接显示文本
                    html.append("<div class='ai-message'>").append(text).append("</div>");
                }
            } else {
                // 代码块
                html.append("<pre><code>").append(parts[i]).append("</code></pre>");
            }
        }

        return String.format(
            "<html><head><style>" +
            "body { margin: 0; padding: 0; color: #DCDCDC; }" +
            // 用户消息样式
            ".user-message { " +
            "   background-color: #2B2B2B; " +
            "   border: 1px solid #646464; " +
            "   border-radius: 5px; " +
            "   padding: 8px; " +
            "   margin: 0; " +
            "   white-space: pre-wrap; " +
            "   word-wrap: break-word; " +
            "}" +
            // AI 消息样式
            ".ai-message { " +
            "   padding: 2px 0; " +  // 减小上下内边距
            "   margin: 0; " +
            "   white-space: pre-wrap; " +
            "   word-wrap: break-word; " +
            "}" +
            // 代码块样式
            "pre { " +
            "   background-color: #2B2B2B; " +
            "   padding: 10px; " +
            "   border-radius: 5px; " +
            "   margin: 4px 0; " +  // 减小代码块的上下边距
            "   overflow-x: auto; " +
            "}" +
            "code { font-family: 'JetBrains Mono', monospace; }" +
            "</style></head><body>%s</body></html>",
            html.toString()
        );
    }

    private JBLabel createConfigLabel() {
        JBLabel label = new JBLabel("<html><u>你还没有配置DeepSeek的API KEY,请点击这里配置！</u></html>");
        label.setForeground(Color.BLUE);
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "DeepSeek");
            }
        });
        
        return label;
    }

    private void checkApiKeyConfig() {
        String apiKey = PropertiesComponent.getInstance().getValue(API_KEY);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            inputArea.setEnabled(false);
            inputArea.putClientProperty("StatusText", "请先配置 API KEY");
        } else {
            inputArea.setEnabled(true);
            inputArea.putClientProperty("StatusText", null);
        }
    }

    // 添加组件大小变化监听
    private void addComponentListener() {
        chatPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                for (Component component : chatPanel.getComponents()) {
                    if (component instanceof JBPanel) {
                        JBPanel<?> bubble = (JBPanel<?>)component;
                        String originalMessage = (String)bubble.getClientProperty("originalMessage");
                        JEditorPane textArea = (JEditorPane)bubble.getClientProperty("textArea");

                        if (textArea != null && originalMessage != null) {
                            // 使用相同的宽度计算逻辑
                            int maxWidth = Math.min(chatPanel.getWidth(), content.getWidth()) - (MESSAGE_HORIZONTAL_MARGIN * 2);
                            
                            // 调整大小
                            textArea.setSize(maxWidth, Short.MAX_VALUE);
                            int preferredHeight = textArea.getPreferredSize().height;
                            ((JPanel)bubble.getComponent(0)).setPreferredSize(
                                new Dimension(maxWidth, preferredHeight + 10)
                            );
                        }
                    }
                }
                
                chatPanel.revalidate();
                chatPanel.repaint();
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
                    String message = (String) bubble.getClientProperty("originalMessage");

                    // 通过判断消息的位置来确定是否为用户消息
                    // 用户消息和 AI 回复总是成对出现，用户消息在偶数位置
                    boolean isUser = (i % 2 == 0);

                    if (message != null && !message.isEmpty()) {
                        messages.add(new ChatMessage(message, isUser));
                    }
                }
            }

            // 限制保存的消息数量
            int historyLimit = PropertiesComponent.getInstance().getInt(HISTORY_LIMIT, 10);
            if (messages.size() > historyLimit * 2) {
                messages = messages.subList(messages.size() - historyLimit * 2, messages.size());
            }

            if (!messages.isEmpty()) {
                String json = gson.toJson(messages);
                PropertiesComponent.getInstance().setValue(CHAT_HISTORY, json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 修改加载聊天记录方法
    private void loadChatHistory() {
        try {
            String json = PropertiesComponent.getInstance().getValue(CHAT_HISTORY);

            if (json != null && !json.isEmpty()) {
                Type listType = new TypeToken<ArrayList<ChatMessage>>(){}.getType();
                List<ChatMessage> messages = gson.fromJson(json, listType);

                if (messages != null && !messages.isEmpty()) {
                    chatPanel.removeAll();

                    for (ChatMessage message : messages) {
                        // 创建消息气泡，传入正确的 isUser 参数
                        JBPanel<JBPanel<?>> bubble = createMessageBubble(message.getMessage(), message.isUser());
                        bubble.putClientProperty("originalMessage", message.getMessage());
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

    // 聊天消息数据类
    private static class ChatMessage {
        private final String message;
        private final boolean user;

        public ChatMessage(String message, boolean user) {
            this.message = message;
            this.user = user;
        }

        public String getMessage() {
            return message;
        }

        public boolean isUser() {
            return user;
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
                Graphics2D g2 = (Graphics2D) g.create();
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

        // 快捷键提示标签
        JLabel hintLabel = new JLabel("按Enter提交，Shift+Enter换行");
        hintLabel.setForeground(new Color(153, 153, 153));
        hintLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        hintLabel.setBorder(JBUI.Borders.empty(0, 8, 6, 8));

        // 创建底部面板
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(hintLabel, BorderLayout.EAST);

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
            JScrollPane scrollPane = (JScrollPane) chatPanel.getParent().getParent();
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
                    if ((step > 0 && newValue >= targetValue) || 
                        (step < 0 && newValue <= targetValue) ||
                        Math.abs(remainingDistance) <= 1) {
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