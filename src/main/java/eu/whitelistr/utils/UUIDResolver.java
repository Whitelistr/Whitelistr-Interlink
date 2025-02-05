package eu.whitelistr.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class UUIDResolver {

    public String resolveUUIDToUsername(String uuid) {
        try {
            URL url = new URL("https://playerdb.co/api/player/minecraft/" + uuid);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Whitelistr-Interlink/1.0 (compatible; +https://example.com/info)");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            Map<String, List<String>> headers = conn.getHeaderFields();
            headers.forEach((key, value) -> System.out.println(key + ": " + value));
            BufferedReader reader;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            System.out.println("Raw response: " + response.toString());

            if (responseCode != HttpURLConnection.HTTP_OK) {
                return null;
            }
            JsonObject jsonResponse = new JsonParser().parse(response.toString()).getAsJsonObject();
            if (jsonResponse.has("data")) {
                JsonObject data = jsonResponse.getAsJsonObject("data");
                if (data.has("player")) {
                    JsonObject player = data.getAsJsonObject("player");
                    if (player.has("username")) {
                        return player.get("username").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to resolve username for UUID " + uuid + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}





