package com.example.design_pattern_prototyping.Monitoring;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

//TODO Replace HttpURLConnection library like Apache HttpClient
// and add retries, and timeouts.
public class PrometheusClient {
    public String queryPrometheus(String query) throws Exception {
        String url = "http://127.0.0.1:9090/api/v1/query?query=" +
                java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } else {
            throw new Exception("Failed to query Prometheus: HTTP code " + responseCode);
        }
    }
}
