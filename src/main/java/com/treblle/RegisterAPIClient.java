package com.treblle;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegisterAPIClient {

    private static final String ANYPOINT_BASE_URL = "https://anypoint.mulesoft.com";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RegisterAPIClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public String getAccessToken(String clientId, String clientSecret) throws IOException, InterruptedException {
        String url = ANYPOINT_BASE_URL + "/accounts/api/v2/oauth2/token";
        String requestBody = String.format("client_id=%s&client_secret=%s&grant_type=client_credentials", clientId,
                clientSecret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode rootNode = objectMapper.readTree(response.body());
            return rootNode.get("access_token").asText();
        } else {
            throw new RuntimeException(
                    "Failed to get access token: " + response.statusCode() + " - " + response.body());
        }
    }

    public List<Map<String, String>> getEnvironments(String accessToken, String organizationId)
            throws IOException, InterruptedException {
        String url = ANYPOINT_BASE_URL + "/accounts/api/organizations/" + organizationId + "/environments";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        List<Map<String, String>> environments = new ArrayList<>();

        if (response.statusCode() == 200) {
            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode dataNode = rootNode.get("data");
            if (dataNode.isArray()) {
                for (JsonNode envNode : dataNode) {
                    Map<String, String> env = new HashMap<>();
                    env.put("id", envNode.get("id").asText());
                    env.put("name", envNode.get("name").asText());
                    environments.add(env);
                }
            }
            return environments;
        } else {
            throw new RuntimeException(
                    "Failed to get environments: " + response.statusCode() + " - " + response.body());
        }
    }

    public List<Map<String, String>> getApis(String accessToken, String organizationId, String environmentId)
            throws IOException, InterruptedException {
        String url = ANYPOINT_BASE_URL + "/apimanager/api/v1/organizations/" + organizationId + "/environments/"
                + environmentId + "/apis";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        List<Map<String, String>> apis = new ArrayList<>();

        if (response.statusCode() == 200) {
            JsonNode rootNode = objectMapper.readTree(response.body());

            JsonNode dataNodeAssets = rootNode.get("assets");
            if (dataNodeAssets.isArray()) {
                for (JsonNode assetNode : dataNodeAssets) {
                    JsonNode dataNode = assetNode.get("apis");
                    if (dataNode.isArray()) {
                        for (JsonNode apiNode : dataNode) {
                            Map<String, String> api = new HashMap<>();
                            api.put("id", apiNode.get("id").asText());
                            api.put("assetId", apiNode.get("assetId").asText());
                            apis.add(api);
                        }
                    }
                }
            }

            return apis;
        } else {
            throw new RuntimeException("Failed to get APIs for environment " + environmentId + ": "
                    + response.statusCode() + " - " + response.body());
        }
    }

    public List<Map<String, String>> getPolicies(String accessToken, String organizationId, String environmentId,
            String apiId) throws IOException, InterruptedException {
        String url = ANYPOINT_BASE_URL + "/apimanager/api/v1/organizations/" + organizationId + "/environments/"
                + environmentId + "/apis/" + apiId + "/policies";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        List<Map<String, String>> policies = new ArrayList<>();

        if (response.statusCode() == 200) {
            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode dataNode = rootNode.get("policies");
            if (dataNode.isArray()) {
                for (JsonNode policyNode : dataNode) {
                    Map<String, String> policy = new HashMap<>();
                    policy.put("id", policyNode.get("policyId").asText());
                    policy.put("name", policyNode.get("type").asText());
                    policy.put("policyTemplateId", policyNode.get("policyTemplateId").asText());

                    // Extract assetId from the template node
                    JsonNode templateNode = policyNode.get("template");
                    if (templateNode != null && templateNode.has("assetId")) {
                        policy.put("assetId", templateNode.get("assetId").asText());
                    }

                    policies.add(policy);
                }
            }
            return policies;
        } else {
            throw new RuntimeException(
                    "Failed to get policies for API " + apiId + ": " + response.statusCode() + " - " + response.body());
        }
    }

    public List<Map<String, Object>> discoverApiPolicies(String clientId, String clientSecret, String organizationId)
            throws IOException, InterruptedException {
        List<Map<String, Object>> apisWithPolicies = new ArrayList<>();
        String accessToken = getAccessToken(clientId, clientSecret);
        System.out.println("Access Token obtained successfully.");

        List<Map<String, String>> environments = getEnvironments(accessToken, organizationId);
        System.out.println("Found " + environments.size() + " environments.");

        for (Map<String, String> env : environments) {
            String envId = env.get("id");
            String envName = env.get("name");
            System.out.println("Processing environment: " + envName + " (ID: " + envId + ")");

            List<Map<String, String>> apis = getApis(accessToken, organizationId, envId);
            System.out.println("  Found " + apis.size() + " APIs in " + envName + ".");

            for (Map<String, String> api : apis) {
                String apiId = api.get("id");
                String apiName = api.get("assetId");
                System.out.println("    Checking policies for API: " + apiName + " (ID: " + apiId + ")");

                List<Map<String, String>> policies = getPolicies(accessToken, organizationId, envId, apiId);

                Map<String, Object> apiEntry = new HashMap<>();
                apiEntry.put("apiName", apiName);
                apiEntry.put("apiId", apiId);
                apiEntry.put("environmentName", envName);
                apiEntry.put("environmentId", envId);
                apiEntry.put("appliedPolicies", policies); // The list of policy maps
                apisWithPolicies.add(apiEntry);

                System.out.println(" API - " + apiName + "-" + envName);

                for (Map<String, String> policy : policies) {

                    System.out.println("Policies - " + policy.get("assetId") + "-" + policy.get("id") + "-" + "proxy");

                }
            }
        }

        return apisWithPolicies;
    }

    // Main method for testing outside Mule (optional)
    public static void main(String[] args) {
        String clientId = "change"; // Replace with your actual client ID
        String clientSecret = "change"; // Replace with your actual client secret
        String organizationId = "change"; // Replace with your actual organization ID

        RegisterAPIClient client = new RegisterAPIClient();
        try {
            List<Map<String, Object>> result = client.discoverApiPolicies(clientId, clientSecret, organizationId);
            System.out.println("\n--- Final API Policy Report ---");
            System.out.println(client.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}