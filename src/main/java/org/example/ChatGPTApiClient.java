package org.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import org.json.JSONArray;
import org.json.JSONObject;

public class ChatGPTApiClient {

    private static Properties config = new Properties();

    // Load configuration once when the class is loaded
    static {
        try (InputStream input = ChatGPTApiClient.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {

            if (input == null) {
                throw new FileNotFoundException("config.properties not found in resources folder");
            }

            config.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void chatGPT(String text) throws Exception {
        // Load values from config.properties
        String apiKey = config.getProperty("api.key");
        String apiUrl = config.getProperty("api.url");
        String model = config.getProperty("api.model");
        double temperature = Double.parseDouble(config.getProperty("api.temperature", "0.7"));
        int maxTokens = Integer.parseInt(config.getProperty("api.max_tokens", "500"));

        // Create connection
        HttpURLConnection con = (HttpURLConnection) new URL(apiUrl).openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + apiKey);

        // Build JSON request
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", text);

        JSONArray messages = new JSONArray();
        messages.put(message);

        JSONObject data = new JSONObject();
        data.put("model", model);
        data.put("messages", messages);
        data.put("temperature", temperature);
        data.put("max_tokens", maxTokens);

        // Send request
        con.setDoOutput(true);
        try (OutputStream os = con.getOutputStream()) {
            os.write(data.toString().getBytes());
        }

        // Read response
        int status = con.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status == 200 ? con.getInputStream() : con.getErrorStream()
        ));

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        if (status == 200) {
            JSONObject obj = new JSONObject(response.toString());
            String reply = obj.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            System.out.println("ChatGPT: " + reply);
        } else {
            System.out.println("Error: " + status + " " + response);
        }
    }

    public static void main(String[] args) throws Exception {
        chatGPT("Hello, how are you?");
    }
}
