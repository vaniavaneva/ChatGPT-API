package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ChatGPTApiClient {

    private final String apiKey; // Stores API key
    private final Properties config = new Properties(); // default settings from config.properties

    // Constructor loads config file if available
    public ChatGPTApiClient(String apiKey) {
        this.apiKey = apiKey;

        // Attempt to load config.properties from resources
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

    // Sends a message to the API and returns response
    public String sendMessage(String model,
                              String prompt,
                              String url,
                              double temperature,
                              int maxTokens) throws IOException {

        // Use defaults if params missing
        if (model == null || model.isEmpty())
            model = config.getProperty("default.model", "gpt-4o-mini");

        if (url == null || url.isEmpty())
            url = config.getProperty("base.url", "https://api.openai.com/v1/chat/completions");

        // Build request body
        JSONObject body = new JSONObject();

        // Construct the messages array with a single "user" message
        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "user").put("content", prompt));

        // Fill fields
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        try {
            // Open HTTP connection to API
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");                                  // POST request
            con.setRequestProperty("Content-Type", "application/json");     // JSON body
            con.setRequestProperty("Authorization", "Bearer " + apiKey);    // API key in header
            con.setDoOutput(true);                                          // Enable writing body

            // Send JSON body
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            // Get HTTP status code
            int status = con.getResponseCode();

            // Choose stream on success/failure
            InputStream inputStream = (status == 200) ? con.getInputStream() : con.getErrorStream();

            // Read response into StringBuilder
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            String responseText = response.toString();

            // If successful (status 200) extract message content
            if (status == 200) {
                JSONObject json = new JSONObject(responseText);
                JSONArray choices = json.optJSONArray("choices");

                if (choices == null || choices.isEmpty()) {
                    return "(No response from API)";
                }

                // Get first choice and message text
                JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                return message.optString("content", "(Empty message)").trim();

            } else {
                // If not successful try to parse error message
                try {
                    JSONObject errJson = new JSONObject(responseText);
                    JSONObject errorObj = errJson.optJSONObject("error");
                    if (errorObj != null && errorObj.has("message")) {
                        return errorObj.getString("message");
                    }
                } catch (Exception ignored) {}

                // Message if no JSON error
                return "API error (" + status + ")";
            }

            // No internet connection
        } catch (UnknownHostException e) {
            return "No internet connection.";

            // Other IO errors (timeouts or refused connections)
        } catch (IOException e) {
            if (e.getMessage() != null && (e.getMessage().contains("timed out") || e.getMessage().contains("refused"))) {
                return "Network error. Unable to reach the API server (" + e.getMessage() + ")";
            }
            throw e; // rethrow for anything else
        }
    }
}
