package org.example;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ChatGPTGui {
    private JPanel panel1;
    private JPasswordField apiKeyField;
    private JTextField urlField;
    private JTextArea promptText;
    private JSpinner tokensSpinner;
    private JSpinner tempSpinner;
    private JButton sendButton;
    private JButton clearButton;
    private JLabel apiKeyLabel;
    private JLabel modelLabel;
    private JLabel urlLabel;
    private JLabel ChatGPTLabel;
    private JLabel tempLabel;
    private JLabel tokLabel;
    private JLabel promptLabel;
    private JLabel answerLabel;
    private JLabel nameFnLabel;
    private JComboBox<String> modelComboBox;
    private JTextPane responsePane;

    private final Properties config = new Properties();
    private ChatGPTApiClient apiClient;

    public ChatGPTGui() {
        loadConfig();
        initUI();
        initApiClient();
        attachHandlers();
    }

    public JPanel getRootPanel() {
        return panel1;
    }

    private void loadConfig() {
        File cfg = new File("config.properties");
        try (InputStream is = cfg.exists()
                ? new FileInputStream(cfg)
                : getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                config.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            } else {
                showWarning("config.properties not found in working directory or classpath.");
            }
        } catch (IOException e) {
            showWarning("Error loading config.properties: " + e.getMessage());
        }
    }

    private void initUI() {
        apiKeyField.setText(config.getProperty("api.key", ""));
        apiKeyField.setEditable(false);
        apiKeyField.setEchoChar('•');

        urlField.setText(config.getProperty("base.url", "https://api.openai.com/v1/chat/completions"));

        modelComboBox.setModel(new DefaultComboBoxModel<>(
                new String[]{"gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo"}));
        modelComboBox.setSelectedItem(config.getProperty("default.model", "gpt-4o-mini"));

        double temp = parseDouble(config.getProperty("default.temp", "0.7"), 0.7);
        int tokens = parseInt(config.getProperty("default.tokens", "512"), 512);
        tempSpinner.setModel(new SpinnerNumberModel(temp, 0.0, 2.0, 0.1));
        tokensSpinner.setModel(new SpinnerNumberModel(tokens, 1, 4096, 1));

        responsePane.setEditable(false);
        try {
            DefaultCaret dc = (DefaultCaret) responsePane.getCaret();
            dc.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        } catch (Exception ignored) {}
    }

    private void initApiClient() {
        String key = new String(apiKeyField.getPassword());
        if (key.isEmpty()) {
            showWarning("API key missing in config.properties");
            apiClient = null;
            return;
        }

        try {
            apiClient = new ChatGPTApiClient(key);
        } catch (Exception e) {
            showError("Failed to initialize API client: " + e.getMessage());
            apiClient = null;
        }
    }

    private void attachHandlers() {
        sendButton.addActionListener(e -> sendMessage());

        // ✅ Clear both the response and the prompt input
        clearButton.addActionListener(e -> {
            responsePane.setText("");
            promptText.setText("");
        });

        promptText.getInputMap().put(KeyStroke.getKeyStroke("ctrl ENTER"), "send");
        promptText.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
    }

    private void sendMessage() {
        if (apiClient == null) {
            initApiClient();
            if (apiClient == null) return;
        }

        String prompt = promptText.getText().trim();
        if (prompt.isEmpty()) {
            showWarning("Please enter a prompt.");
            return;
        }

        String model = modelComboBox.getSelectedItem().toString();
        String url = urlField.getText().trim();
        int tokens = (Integer) tokensSpinner.getValue();
        double temp = (Double) tempSpinner.getValue();

        sendButton.setEnabled(false);

        new Thread(() -> {
            try {
                String reply = apiClient.sendMessage(model, prompt, url, temp, tokens);
                // ✅ Show only the assistant's message (clean text)
                setResponseText(reply + "\n");
            } catch (Exception ex) {
                // ✅ Show only the readable error message
                setResponseText(ex.getMessage() + "\n");
            } finally {
                SwingUtilities.invokeLater(() -> sendButton.setEnabled(true));
            }
        }).start();
    }

    private void setResponseText(String text) {
        SwingUtilities.invokeLater(() -> responsePane.setText(text));
    }

    private void showWarning(String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE));
    }

    private void showError(String msg) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE));
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatGPTGui gui = new ChatGPTGui();
            JFrame frame = new JFrame("ChatGPT Client");
            frame.setContentPane(gui.getRootPanel());
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setSize(500, 400);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
