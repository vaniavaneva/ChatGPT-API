package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ChatGPTApiClient {

    private final String apiKey;
    private final Properties config = new Properties();

    public ChatGPTApiClient(String apiKey) {
        this.apiKey = apiKey;

        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                config.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            System.err.println("Warning: could not load config.properties: " + e.getMessage());
        }
    }

    /**
     * Sends a chat request and returns only the assistant message or the readable error message.
     */
    public String sendMessage(String model,
                              String prompt,
                              String url,
                              double temperature,
                              int maxTokens) throws IOException {

        if (model == null || model.isEmpty())
            model = config.getProperty("default.model", "gpt-4o-mini");

        if (url == null || url.isEmpty())
            url = config.getProperty("base.url", "https://api.openai.com/v1/chat/completions");

        JSONObject body = new JSONObject();
        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "user").put("content", prompt));

        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + apiKey);
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = con.getResponseCode();
        InputStream inputStream = (status == 200) ? con.getInputStream() : con.getErrorStream();

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        String responseText = response.toString();

        if (status == 200) {
            JSONObject json = new JSONObject(responseText);
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return "(No response from API)";
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            return message.optString("content", "(Empty message)").trim();
        } else {
            try {
                JSONObject errJson = new JSONObject(responseText);
                JSONObject errorObj = errJson.optJSONObject("error");
                if (errorObj != null && errorObj.has("message")) {
                    return errorObj.getString("message");
                }
            } catch (Exception ignored) {}
            return "API error (" + status + ")";
        }
    }
}