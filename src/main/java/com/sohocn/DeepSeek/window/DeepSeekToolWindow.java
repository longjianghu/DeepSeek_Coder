package com.sohocn.DeepSeek.window;

import java.awt.*;
import java.awt.event.*;

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
        
        // 输入区域
        inputArea = new JBTextArea();
        inputArea.setRows(5);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);

        // 创建字数统计标签
        charCountLabel = new JLabel("0 字");
        charCountLabel.setForeground(Color.GRAY);
        charCountLabel.setBorder(JBUI.Borders.empty(2, 5));

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

        // 创建输入区域面板，包含输入框和字数统计
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(backgroundColor);
        JBScrollPane inputScrollPane = new JBScrollPane(inputArea);
        inputScrollPane.setBorder(JBUI.Borders.empty(5));
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        inputPanel.add(charCountLabel, BorderLayout.SOUTH);

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
            addMessageBubble("正在思考中...", false);

            // 在后台线程中发送请求
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    String response = deepSeekService.sendMessage(message);

                    // 在 UI 线程中更新界面
                    SwingUtilities.invokeLater(() -> {
                        // 移除"正在思考中..."
                        chatPanel.remove(chatPanel.getComponentCount() - 1);
                        // 显示 AI 回复
                        addMessageBubble(response, false);
                        // 重新启用输入框
                        inputArea.setEnabled(true);
                        inputArea.requestFocus();
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        // 移除"正在思考中..."
                        chatPanel.remove(chatPanel.getComponentCount() - 1);
                        // 显示错误消息
                        addMessageBubble("Error: " + e.getMessage(), false);
                        // 重新启用输入框
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

        if (isUser) {
            // 用户消息使用普通文本
            JTextArea textArea = new JTextArea(message);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBackground(new Color(0, 122, 255));
            textArea.setForeground(Color.WHITE);
            textArea.setBorder(JBUI.Borders.empty(8));

            // 设置最大宽度为面板宽度的 80%
            int maxWidth = (int)(chatPanel.getWidth() * 0.8);
            if (maxWidth > 0) {
                textArea.setSize(maxWidth, Short.MAX_VALUE);
                textArea.setPreferredSize(new Dimension(maxWidth, textArea.getPreferredSize().height));
            }

            bubble.add(textArea, BorderLayout.EAST);
        } else {
            // AI 回复使用 Markdown 渲染
            JEditorPane editorPane = new JEditorPane();
            editorPane.setEditorKit(createMarkdownEditorKit());
            editorPane.setEditable(false);
            editorPane.setOpaque(true);
            editorPane.setBackground(new Color(58, 58, 58));
            editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

            // 渲染 Markdown
            Node document = markdownParser.parse(message);
            String html = htmlRenderer.render(document);
            editorPane.setText(wrapHtmlContent(html));

            // 设置最大宽度为面板宽度的 80%
            int maxWidth = (int)(chatPanel.getWidth() * 0.8);
            if (maxWidth > 0) {
                editorPane.setSize(maxWidth, Short.MAX_VALUE);
                editorPane.setPreferredSize(new Dimension(maxWidth, editorPane.getPreferredSize().height));
            }

            bubble.add(editorPane, BorderLayout.WEST);
        }

        bubble.setBorder(JBUI.Borders.empty(5, 15));
        return bubble;
    }

    private HTMLEditorKit createMarkdownEditorKit() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();

        // 添加 Markdown 样式
        styleSheet
            .addRule(
                "body { color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Arial, sans-serif; }");
        styleSheet.addRule("code { background-color: #2B2B2B; padding: 2px 4px; border-radius: 3px; }");
        styleSheet.addRule("pre { background-color: #2B2B2B; padding: 10px; border-radius: 5px; overflow-x: auto; }");
        styleSheet.addRule("a { color: #589DF6; }");
        styleSheet.addRule("p { margin: 0; padding: 0; }");
        styleSheet.addRule("ul, ol { margin: 0; padding-left: 20px; }");

        return kit;
    }

    private String wrapHtmlContent(String html) {
        return "<html><body style='background-color: #3A3A3A; padding: 8px;'>" + html + "</body></html>";
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