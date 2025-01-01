package com.sohocn.DeepSeek.window;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
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

public class DeepSeekToolWindow {
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";
    private final JPanel content;
    private final JBPanel<JBPanel<?>> chatPanel; // 用于存放消息气泡
    private final JBTextArea inputArea;
    private final Project project;
    private JBLabel configLabel;
    private JLabel charCountLabel; // 添加字数统计标签
    private final DeepSeekService deepSeekService;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public DeepSeekToolWindow(Project project) {
        this.project = project;
        this.deepSeekService = new DeepSeekService();

        // 初始化 Markdown 解析器
        MutableDataSet options = new MutableDataSet();
        markdownParser = Parser.builder(options).build();
        htmlRenderer = HtmlRenderer.builder(options).build();

        content = new JPanel(new BorderLayout());
        
        // 设置背景色与编辑器一致
        Color backgroundColor = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
        content.setBackground(backgroundColor);

        // 聊天区域
        chatPanel = new JBPanel<>(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false));
        chatPanel.setBackground(backgroundColor);
        JBScrollPane chatScrollPane = new JBScrollPane(chatPanel);
        chatScrollPane.setBackground(backgroundColor);
        chatScrollPane.setBorder(JBUI.Borders.empty());
        
        // 创建字数统计标签（移到这里）
        charCountLabel = new JLabel("0 字");
        charCountLabel.setForeground(Color.GRAY);
        charCountLabel.setBorder(JBUI.Borders.empty(2, 5));
        
        // 输入区域
        inputArea = new JBTextArea();
        inputArea.setRows(5);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        
        // 自定义输入框样式
        inputArea.setBorder(JBUI.Borders.empty(8));
        inputArea.setBackground(new Color(58, 58, 58));
        inputArea.setForeground(Color.WHITE);
        inputArea.setCaretColor(Color.WHITE);
        inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        // 创建一个带圆角和边框的面板来包装输入框
        JPanel inputWrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 绘制圆角背景
                g2.setColor(new Color(58, 58, 58));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                
                // 绘制边框
                g2.setColor(new Color(80, 80, 80));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                
                g2.dispose();
            }
        };
        inputWrapper.setOpaque(false);
        inputWrapper.add(inputArea);
        inputWrapper.setBorder(JBUI.Borders.empty(2));

        // 创建输入区域面板，包含输入框和字数统计
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(backgroundColor);
        inputPanel.setBorder(JBUI.Borders.empty(10));
        inputPanel.add(inputWrapper, BorderLayout.CENTER);
        inputPanel.add(charCountLabel, BorderLayout.SOUTH);

        // 监听输入变化
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            private void updateCharCount() {
                int count = inputArea.getText().length();
                charCountLabel.setText(count + " 字");
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

        // 创建一个面板来包含configLabel，使其顶部对齐
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(backgroundColor);
        configLabel = createConfigLabel();
        topPanel.add(configLabel, BorderLayout.NORTH);
        
        content.add(topPanel, BorderLayout.NORTH);
        content.add(chatScrollPane, BorderLayout.CENTER);
        content.add(inputPanel, BorderLayout.SOUTH);

        // 订阅API Key变更事件
        ApplicationManager.getApplication().getMessageBus().connect()
            .subscribe(ApiKeyChangeNotifier.TOPIC, event -> {
                SwingUtilities.invokeLater(this::checkApiKeyConfig);
            });

        checkApiKeyConfig();

        addComponentListener(); // 添加大小变化监听
    }

    private void sendMessage() {
        String message = inputArea.getText().trim();
        if (!message.isEmpty()) {
            // 显示用户消息
            addMessageBubble(message, true);
            inputArea.setText("");

            // 禁用输入框，显示正在处理
            inputArea.setEnabled(false);
            
            // 创建 AI 回复的气泡
            JBPanel<JBPanel<?>> aiBubble = createMessageBubble("", false);
            JScrollPane scrollPane = (JScrollPane) aiBubble.getComponent(0);
            JEditorPane textArea = (JEditorPane) aiBubble.getClientProperty("textArea");

            chatPanel.add(aiBubble);
            chatPanel.revalidate();
            chatPanel.repaint();

            // 在后台线程中发送请求
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    StringBuilder fullResponse = new StringBuilder();
                    
                    deepSeekService.streamMessage(
                        message,
                        // 处理每个文本块
                        chunk -> SwingUtilities.invokeLater(() -> {
                            fullResponse.append(chunk);
                            
                            // 更新原始消息内容
                            @SuppressWarnings("unchecked")
                            Consumer<String> updateMessage = (Consumer<String>) aiBubble.getClientProperty("updateMessage");
                            if (updateMessage != null) {
                                updateMessage.accept(fullResponse.toString());
                            }
                            
                            // 渲染 Markdown
                            Node document = markdownParser.parse(fullResponse.toString());
                            String html = htmlRenderer.render(document);
                            textArea.setText(wrapHtmlContent(html));
                            
                            // 调整大小
                            int maxWidth = chatPanel.getWidth() - 80;
                            if (maxWidth > 0) {
                                textArea.setSize(maxWidth, Short.MAX_VALUE);
                                int preferredHeight = textArea.getPreferredSize().height;
                                scrollPane.setPreferredSize(new Dimension(maxWidth, Math.min(preferredHeight + 32, 500)));
                            }
                            
                            // 重新布局
                            aiBubble.revalidate();
                            chatPanel.revalidate();
                            chatPanel.repaint();
                            
                            // 滚动到底部
                            SwingUtilities.invokeLater(() -> {
                                JScrollBar vertical = ((JScrollPane)chatPanel.getParent().getParent()).getVerticalScrollBar();
                                vertical.setValue(vertical.getMaximum());
                            });
                        }),
                        // 处理 token 使用情况
                        tokenUsage -> SwingUtilities.invokeLater(() -> {
                            JLabel tokenLabel = (JLabel)aiBubble.getClientProperty("tokenLabel");
                            if (tokenLabel != null) {
                                tokenLabel
                                    .setText(String
                                        .format("Tokens: %d prompt, %d completion, %d total", tokenUsage.promptTokens,
                                            tokenUsage.completionTokens, tokenUsage.totalTokens));
                            }
                        }),
                        // 完成回调
                        () -> SwingUtilities.invokeLater(() -> {
                            inputArea.setEnabled(true);
                            inputArea.requestFocus();
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
        }
    }

    private void addMessageBubble(String message, boolean isUser) {
        JBPanel<JBPanel<?>> bubble = createMessageBubble(message, isUser);
        chatPanel.add(bubble);
        chatPanel.revalidate();
        chatPanel.repaint();

        // 滚动到底部
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = ((JScrollPane)chatPanel.getParent().getParent()).getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private JBPanel<JBPanel<?>> createMessageBubble(String message, boolean isUser) {
        JBPanel<JBPanel<?>> bubble = new JBPanel<>(new BorderLayout());
        bubble.setBackground(null);

        // 创建文本区域（使用 JEditorPane 支持 Markdown）
        JEditorPane textArea = new JEditorPane();
        textArea.setEditorKit(createMarkdownEditorKit());
        textArea.setEditable(false);
        textArea.setBorder(JBUI.Borders.empty(8));
        textArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        // 创建底部面板，包含 token 信息和复制图标
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        bottomPanel.setOpaque(false);

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
        }

        // 创建一个带圆角边框的面板
        JPanel roundedPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 绘制圆角背景
                g2.setColor(isUser ? new Color(0, 122, 255) : new Color(58, 58, 58));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

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
            textArea.setBackground(new Color(0, 122, 255));
            textArea.setForeground(Color.WHITE);
            textArea.setText(message);
            bubble.add(roundedPanel, BorderLayout.EAST);
        } else {
            textArea.setBackground(new Color(58, 58, 58));
            textArea.setForeground(Color.WHITE);
            // 渲染 Markdown
            Node document = markdownParser.parse(message);
            String html = htmlRenderer.render(document);
            textArea.setText(wrapHtmlContent(html));
            bubble.add(roundedPanel, BorderLayout.WEST);
        }

        // 将内容面板放入滚动面板
        JScrollPane scrollPane = new JScrollPane(roundedPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        // 设置宽度为面板宽度减去边距
        int maxWidth = chatPanel.getWidth() - 80;
        if (maxWidth > 0) {
            textArea.setSize(maxWidth, Short.MAX_VALUE);
            int preferredHeight = textArea.getPreferredSize().height;
            scrollPane.setPreferredSize(new Dimension(maxWidth, Math.min(preferredHeight + 32, 500)));
        }

        bubble.add(scrollPane);
        bubble.setBorder(JBUI.Borders.empty(5, 15));

        // 保存 token 标签的引用
        bubble.putClientProperty("tokenLabel", tokenLabel);

        // 在 sendMessage 方法的回调中更新消息内容
        if (!isUser) {
            bubble.putClientProperty("updateMessage", (Consumer<String>)newMessage -> {
                currentMessage[0] = newMessage;
            });
        }

        // 保存文本区域的引用
        bubble.putClientProperty("textArea", textArea);

        return bubble;
    }

    private HTMLEditorKit createMarkdownEditorKit() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();

        // 添加 Markdown 样式
        styleSheet
            .addRule(
                "body { color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Arial, sans-serif; margin: 0; padding: 0; }");
        styleSheet
            .addRule(
                "code { background-color: #2B2B2B; padding: 2px 4px; border-radius: 3px; font-family: 'JetBrains Mono', monospace; word-wrap: break-word; }");
        styleSheet
            .addRule(
                "pre { background-color: #2B2B2B; padding: 10px; border-radius: 5px; margin: 10px 0; white-space: pre-wrap; max-width: 100%; }");
        styleSheet.addRule("pre code { background-color: transparent; padding: 0; }");
        styleSheet.addRule("* { max-width: 100%; }"); // 确保所有元素不超出容器
        styleSheet.addRule("img { max-width: 100%; height: auto; }"); // 图片自适应
        styleSheet.addRule("a { color: #589DF6; }");
        styleSheet.addRule("p { margin: 8px 0; padding: 0; }");
        styleSheet.addRule("ul, ol { margin: 8px 0; padding-left: 20px; }");
        styleSheet.addRule("li { margin: 4px 0; }");
        styleSheet
            .addRule(
                "blockquote { margin: 8px 0; padding-left: 10px; border-left: 3px solid #4A4A4A; color: #BBBBBB; }");
        styleSheet.addRule("strong { color: #FFFFFF; font-weight: bold; }");

        return kit;
    }

    private String wrapHtmlContent(String html) {
        // 设置固定宽度和自动换行
        return String
            .format("<html><head><style>"
                + "body { background-color: #3A3A3A; margin: 0; padding: 0; width: %dpx; word-wrap: break-word; }"
                + "pre { white-space: pre-wrap; max-width: 100%%; overflow-x: hidden; }"
                + "code { word-wrap: break-word; white-space: pre-wrap; }" + "</style></head><body>%s</body></html>",
                chatPanel.getWidth() - 100, // 减去足够的边距
                html);
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
            configLabel.setVisible(true);
            chatPanel.setVisible(false);
            inputArea.setEnabled(false);
            charCountLabel.setVisible(false);
        } else {
            configLabel.setVisible(false);
            chatPanel.setVisible(true);
            inputArea.setEnabled(true);
            charCountLabel.setVisible(true);
        }
    }

    // 添加组件大小变化监听
    private void addComponentListener() {
        chatPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // 重新布局所有消息气泡
                for (Component component : chatPanel.getComponents()) {
                    if (component instanceof JBPanel) {
                        Component messageComponent = ((JBPanel<?>)component).getComponent(0);
                        int maxWidth = (int)(chatPanel.getWidth() * 0.8);
                        if (maxWidth > 0) {
                            messageComponent.setSize(maxWidth, Short.MAX_VALUE);
                            messageComponent
                                .setPreferredSize(new Dimension(maxWidth, messageComponent.getPreferredSize().height));
                        }
                    }
                }
                chatPanel.revalidate();
                chatPanel.repaint();
            }
        });
    }

    public JPanel getContent() {
        return content;
    }
} 