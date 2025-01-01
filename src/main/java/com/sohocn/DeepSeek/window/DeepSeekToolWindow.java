package com.sohocn.DeepSeek.window;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

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
import com.sohocn.DeepSeek.service.DeepSeekService;
import com.sohocn.DeepSeek.settings.ApiKeyChangeNotifier;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import groovyjarjarantlr4.v4.runtime.misc.NotNull;

public class DeepSeekToolWindow {
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";
    private static final String CHAT_HISTORY = "com.sohocn.deepseek.chatHistory";
    private static final String HISTORY_LIMIT = "com.sohocn.deepseek.historyLimit";
    private static final Gson gson = new GsonBuilder().create();
    private final JPanel content;
    private final JBPanel<JBPanel<?>> chatPanel;
    private final JBTextArea inputArea = new JBTextArea();
    private final Project project;
    private JLabel charCountLabel;
    private final DeepSeekService deepSeekService;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private static final int MESSAGE_HORIZONTAL_MARGIN = 30; // 每侧 30 像素的边距
    private static final int MESSAGE_TOTAL_MARGIN = MESSAGE_HORIZONTAL_MARGIN * 2; // 两侧总共 60 像素的边距

    public DeepSeekToolWindow(Project project) {
        this.project = project;
        this.deepSeekService = new DeepSeekService();

        // 初始化 Markdown 解析器
        MutableDataSet options = new MutableDataSet();
        markdownParser = Parser.builder(options).build();
        htmlRenderer = HtmlRenderer.builder(options).build();

        content = new JPanel(new BorderLayout());
        
        // 使用更深的背景色
        Color backgroundColor = new Color(30, 30, 30); // 使用固定的深色背景
        content.setBackground(backgroundColor);

        // 聊天区域
        chatPanel = new JBPanel<>(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false));
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

        // 监听输入变化
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            private void updateCharCount() {
                int count = inputArea.getText().length();
                charCountLabel.setText(count + " 字");
                charCountLabel.setVisible(true);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCharCount();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCharCount();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCharCount();
            }
        });

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
            charCountLabel.setText("0 字,按Enter提交，Shift+Enter换行"); // 重置字数统计

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
                            // 处理每个文本块
                            chunk -> SwingUtilities.invokeLater(() -> {
                                fullResponse.append(chunk);
                                String currentResponse = fullResponse.toString();

                                // 保存原始消息
                                aiBubble.putClientProperty("originalMessage", currentResponse);

                                // 更新原始消息内容
                                @SuppressWarnings("unchecked")
                                Consumer<String> updateMessage = (Consumer<String>) aiBubble.getClientProperty("updateMessage");
                                if (updateMessage != null) {
                                    updateMessage.accept(currentResponse);
                                }

                                // 渲染 Markdown
                                Node document = markdownParser.parse(currentResponse);
                                String html = htmlRenderer.render(document);
                                JEditorPane textArea = (JEditorPane) aiBubble.getClientProperty("textArea");
                                textArea.setText(wrapHtmlContent(html));

                                // 调整大小
                                int maxWidth = Math.min(chatPanel.getWidth() - MESSAGE_TOTAL_MARGIN - 40, 600);
                                if (maxWidth > 0) {
                                    textArea.setSize(maxWidth, Short.MAX_VALUE);
                                    int preferredHeight = textArea.getPreferredSize().height;
                                    ((JPanel)aiBubble.getComponent(0)).setPreferredSize(
                                        new Dimension(maxWidth, preferredHeight + 32)
                                    );
                                }

                                // 重新布局
                                aiBubble.revalidate();
                                chatPanel.revalidate();
                            }),
                            // 处理 token 使用情况
                            tokenUsage -> SwingUtilities.invokeLater(() -> {
                                JLabel tokenLabel = (JLabel) aiBubble.getClientProperty("tokenLabel");
                                JPanel bottomPanel = (JPanel) aiBubble.getClientProperty("bottomPanel");
                                if (tokenLabel != null && bottomPanel != null) {
                                    tokenLabel.setText(String.format(
                                        "Tokens: %d prompt, %d completion, %d total",
                                        tokenUsage.promptTokens,
                                        tokenUsage.completionTokens,
                                        tokenUsage.totalTokens
                                    ));
                                    bottomPanel.setVisible(true);
                                    
                                    // 在显示 token 信息后滚动到底部
                                    scrollToBottom();
                                }
                            }),
                            // 完成回调
                            () -> SwingUtilities.invokeLater(() -> {
                                inputArea.setEnabled(true);
                                inputArea.requestFocus();
                                // 完成时也滚动到底部
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

        // 创建文本区域
        JEditorPane textArea = new JEditorPane();
        textArea.setEditorKit(createMarkdownEditorKit());
        textArea.setEditable(false);
        textArea.setBorder(JBUI.Borders.empty(8));
        textArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        // 允许水平滚动
        textArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        // 创建底部面板，包含 token 信息和复制图标
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(JBUI.Borders.empty(8, 0, 8, 0));

        // Token 信息标签
        JLabel tokenLabel = new JLabel("");
        tokenLabel.setForeground(new Color(153, 153, 153));
        tokenLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        // 复制图标按钮
        JLabel copyIcon = new JLabel("📋");
        copyIcon.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        copyIcon.setForeground(new Color(153, 153, 153));
        copyIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));
        copyIcon.setToolTipText("复制内容");

        // 保存原始消息的引用
        final String[] currentMessage = {message};

        copyIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isUser) {
                    String contentToCopy = currentMessage[0];
                    if (contentToCopy != null && !contentToCopy.isEmpty()) {
                        StringSelection selection = new StringSelection(contentToCopy);
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(selection, selection);

                        // 可选：显示复制成功提示
                        copyIcon.setToolTipText("复制成功！");
                        Timer timer = new Timer(1500, evt -> copyIcon.setToolTipText("复制内容"));
                        timer.setRepeats(false);
                        timer.start();
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                copyIcon.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                copyIcon.setForeground(new Color(153, 153, 153));
            }
        });

        if (!isUser) {
            bottomPanel.add(tokenLabel);
            bottomPanel.add(copyIcon);
            bottomPanel.setVisible(false); // 初始时隐藏，等有 token 信息时再显示
        }

        // 创建一个带圆角边框的面板
        JPanel roundedPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 绘制圆角背景（只为 AI 回复添加背景色）
                if (!isUser) {
                    g2.setColor(new Color(58, 58, 58));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                }

                // 绘制边框
                g2.setColor(new Color(80, 80, 80));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

                g2.dispose();
            }
        };
        roundedPanel.setOpaque(false);

        // 创建一个面板来包含文本区域和底部面板
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.add(textArea, BorderLayout.CENTER);
        if (!isUser) {
            contentPanel.add(bottomPanel, BorderLayout.SOUTH);
        }
        roundedPanel.add(contentPanel);

        // 设置颜色和内容
        if (isUser) {
            textArea.setBackground(null);
            textArea.setForeground(Color.WHITE);
            textArea.setText(message);
            bubble.add(roundedPanel, BorderLayout.EAST);

            int maxWidth = Math.min(chatPanel.getWidth() - MESSAGE_TOTAL_MARGIN, 600); // 限制最大宽度
            if (maxWidth > 0) {
                textArea.setSize(maxWidth, Short.MAX_VALUE);
                int preferredHeight = textArea.getPreferredSize().height;
                roundedPanel.setPreferredSize(new Dimension(maxWidth, preferredHeight + 16));
            }
        } else {
            textArea.setBackground(new Color(58, 58, 58));
            textArea.setForeground(Color.WHITE);
            
            // 检查消息是否包含代码块
            if (message.contains("```") || message.contains("`")) {
                // 包含代码块，使用 Markdown 渲染
                Node document = markdownParser.parse(message);
                String html = htmlRenderer.render(document);
                textArea.setContentType("text/html");
                textArea.setText(wrapHtmlContent(html));
            } else {
                // 不包含代码块，使用简单的 HTML 包装纯文本
                textArea.setContentType("text/html");
                String wrappedText = String.format(
                    "<html><body style='margin: 0; padding: 0; white-space: pre-wrap;'>%s</body></html>",
                    message.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
                );
                textArea.setText(wrappedText);
            }
            
            bubble.add(roundedPanel, BorderLayout.WEST);

            int maxWidth = Math.min(chatPanel.getWidth() - MESSAGE_TOTAL_MARGIN - 40, 600);
            if (maxWidth > 0) {
                textArea.setSize(maxWidth, Short.MAX_VALUE);
                int preferredHeight = textArea.getPreferredSize().height;
                roundedPanel.setPreferredSize(new Dimension(maxWidth, preferredHeight + 32));
            }
        }

        // 将内容面板放入面板（不使用滚动面板）
        bubble.add(roundedPanel);
        bubble.setBorder(JBUI.Borders.empty(5, 15));

        // 保存 token 标签的引用
        bubble.putClientProperty("tokenLabel", tokenLabel);

        // 在 sendMessage 方法的回调中更新消息内容
        if (!isUser) {
            bubble.putClientProperty("updateMessage", (Consumer<String>)newMessage -> {
                currentMessage[0] = newMessage;
            });
            bubble.putClientProperty("bottomPanel", bottomPanel); // 保存底部面板的引用
        }

        // 保存文本区域的引用
        bubble.putClientProperty("textArea", textArea);

        return bubble;
    }

    private HTMLEditorKit createMarkdownEditorKit() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();

        styleSheet
            .addRule(
                "body { color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Arial, sans-serif; }");
        styleSheet.addRule("pre { background-color: #2B2B2B; margin: 10px 0; border-radius: 5px; }");
        styleSheet.addRule("pre code { font-family: 'JetBrains Mono', monospace; }");
        styleSheet
            .addRule(
                "code { background-color: #2B2B2B; padding: 2px 4px; border-radius: 3px; font-family: 'JetBrains Mono', monospace; }");
        styleSheet.addRule("p { margin: 8px 0; }");
        styleSheet.addRule("a { color: #589DF6; }");
        styleSheet.addRule("img { max-width: 100%; }");
        styleSheet.addRule("table { width: 100%; border-collapse: collapse; margin: 10px 0; }");
        styleSheet.addRule("td, th { border: 1px solid #4A4A4A; padding: 8px; }");

        return kit;
    }

    private String wrapHtmlContent(String html) {
        int maxWidth = Math.min(chatPanel.getWidth() - MESSAGE_TOTAL_MARGIN - 40, 600);
        return String.format(
            "<html><head><style>" +
            "body { background-color: transparent; margin: 0; padding: 0; width: %dpx; }" +
            // 普通文本样式
            "body > *:not(pre) { white-space: pre-wrap; margin: 8px 0; }" +
            // 代码块样式
            "pre { margin: 10px 0; background-color: #2B2B2B; padding: 10px; border-radius: 5px; " +
            "     overflow-x: auto; max-width: %dpx; }" +
            "pre code { white-space: pre; font-family: 'JetBrains Mono', monospace; }" +
            // 其他基本样式
            "img { max-width: 100%%; }" +
            "table { width: 100%%; border-collapse: collapse; }" +
            "td, th { border: 1px solid #4A4A4A; padding: 8px; }" +
            "</style></head><body>%s</body></html>",
            maxWidth,
            maxWidth,
            html
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
            charCountLabel.setVisible(false);
        } else {
            inputArea.setEnabled(true);
            inputArea.putClientProperty("StatusText", null);
            charCountLabel.setVisible(true);
            charCountLabel.setText(String.format("%d 字,按Enter提交，Shift+Enter换行", 
                inputArea.getText().replaceAll("^\\s+|\\s+$", "").length()));
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
                            int maxWidth = Math.min(chatPanel.getWidth() - MESSAGE_TOTAL_MARGIN - 40, 600);
                            
                            // 只有包含代码块的消息才需要重新渲染
                            if (originalMessage.contains("```") || originalMessage.contains("`")) {
                                Node document = markdownParser.parse(originalMessage);
                                String html = htmlRenderer.render(document);
                                textArea.setText(wrapHtmlContent(html));
                            }
                            
                            // 调整大小
                            textArea.setSize(maxWidth, Short.MAX_VALUE);
                            int preferredHeight = textArea.getPreferredSize().height;
                            ((JPanel)bubble.getComponent(0)).setPreferredSize(
                                new Dimension(maxWidth, preferredHeight + 32)
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

            for (Component component : components) {
                if (component instanceof JBPanel) {
                    JBPanel<?> bubble = (JBPanel<?>) component;
                    String message = (String) bubble.getClientProperty("originalMessage");
                    boolean isUser = bubble.getComponent(0) instanceof JPanel && 
                                   !((JPanel)bubble.getComponent(0)).isOpaque();

                    // 获取 token 信息
                    TokenInfo tokenInfo = null;
                    if (!isUser) {
                        JLabel tokenLabel = (JLabel) bubble.getClientProperty("tokenLabel");
                        if (tokenLabel != null && tokenLabel.getText() != null) {
                            String tokenText = tokenLabel.getText();
                            try {
                                String[] parts = tokenText.split("[^0-9]+");
                                if (parts.length >= 4) {
                                    tokenInfo = new TokenInfo(
                                        Integer.parseInt(parts[1]),
                                        Integer.parseInt(parts[2]),
                                        Integer.parseInt(parts[3])
                                    );
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if (message != null && !message.isEmpty()) {
                        messages.add(new ChatMessage(message, isUser, tokenInfo));
                        System.out.println("Saving message: " + message + ", tokenInfo: " + 
                            (tokenInfo != null ? String.format("prompt=%d, completion=%d, total=%d",
                                tokenInfo.promptTokens, tokenInfo.completionTokens, tokenInfo.totalTokens) : "null"));
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
                System.out.println("Saved chat history: " + messages.size() + " messages"); // 添加日志
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 加载聊天记录
    private void loadChatHistory() {
        try {
            String json = PropertiesComponent.getInstance().getValue(CHAT_HISTORY);
            System.out.println("Loading chat history: " + json);

            if (json != null && !json.isEmpty()) {
                Type listType = new TypeToken<ArrayList<ChatMessage>>(){}.getType();
                List<ChatMessage> messages = gson.fromJson(json, listType);

                if (messages != null && !messages.isEmpty()) {
                    chatPanel.removeAll();

                    for (ChatMessage message : messages) {
                        JBPanel<JBPanel<?>> bubble = createMessageBubble(message.getMessage(), message.isUser());
                        bubble.putClientProperty("originalMessage", message.getMessage());

                        if (!message.isUser()) {
                            // 渲染 Markdown
                            JEditorPane textArea = (JEditorPane) bubble.getClientProperty("textArea");
                            if (textArea != null) {
                                Node document = markdownParser.parse(message.getMessage());
                                String html = htmlRenderer.render(document);
                                textArea.setText(wrapHtmlContent(html));
                            }

                            // 恢复 token 信息
                            TokenInfo tokenInfo = message.getTokenInfo();
                            if (tokenInfo != null) {
                                JLabel tokenLabel = (JLabel) bubble.getClientProperty("tokenLabel");
                                JPanel bottomPanel = (JPanel) bubble.getClientProperty("bottomPanel");
                                if (tokenLabel != null && bottomPanel != null) {
                                    String tokenText = String.format("Tokens: %d prompt, %d completion, %d total",
                                        tokenInfo.promptTokens, tokenInfo.completionTokens, tokenInfo.totalTokens);
                                    tokenLabel.setText(tokenText);
                                    bottomPanel.setVisible(true);
                                    System.out.println("Restored token info for message: " + tokenText);
                                }
                            }
                        }

                        chatPanel.add(bubble);
                    }

                    chatPanel.revalidate();
                    chatPanel.repaint();
                    smoothScrollToBottom();
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
        private final TokenInfo tokenInfo; // 添加 token 信息

        public ChatMessage(String message, boolean user, TokenInfo tokenInfo) {
            this.message = message;
            this.user = user;
            this.tokenInfo = tokenInfo;
        }

        public String getMessage() {
            return message;
        }

        public boolean isUser() {
            return user;
        }

        public TokenInfo getTokenInfo() {
            return tokenInfo;
        }
    }

    // 添加 TokenInfo 类
    private static class TokenInfo {
        private final int promptTokens;
        private final int completionTokens;
        private final int totalTokens;

        public TokenInfo(int promptTokens, int completionTokens, int totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        @Override
        public String toString() {
            return String.format("TokenInfo{prompt=%d, completion=%d, total=%d}",
                promptTokens, completionTokens, totalTokens);
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

            // 添加这个方法来确保面板是完全不透明的
            @Override
            public boolean isOpaque() {
                return false;
            }
        };
        borderPanel.setBorder(JBUI.Borders.empty(1)); // 添加小边距以防止边框被裁剪

        // 输入框
        inputArea.setBackground(new Color(43, 43, 43));
        inputArea.setForeground(new Color(220, 220, 220));
        inputArea.setCaretColor(Color.WHITE);
        inputArea.setBorder(JBUI.Borders.empty(8, 8, 24, 8));
        inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setRows(3);
        inputArea.setOpaque(false); // 设置为透明，让背景色显示出来

        // 字数统计标签
        charCountLabel = new JLabel("0 字,按Enter提交，Shift+Enter换行");
        charCountLabel.setForeground(new Color(153, 153, 153));
        charCountLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        charCountLabel.setBorder(JBUI.Borders.empty(0, 8, 6, 8));

        // 创建底部面板
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(charCountLabel, BorderLayout.EAST);

        // 组装面板
        borderPanel.add(inputArea, BorderLayout.CENTER);
        borderPanel.add(bottomPanel, BorderLayout.SOUTH);
        inputPanel.add(borderPanel, BorderLayout.CENTER);

        // 监听输入变化
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            private void updateCharCount() {
                // 获取输入内容并过滤前后空格后计算字数
                int count = inputArea.getText().replaceAll("^\\s+|\\s+$", "").length();
                charCountLabel.setText(String.format("%d 字,按Enter提交，Shift+Enter换行", count));
                charCountLabel.setVisible(true);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCharCount();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCharCount();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCharCount();
            }
        });

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