package com.treblle;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterAPIClient {

    private static final String ANYPOINT_BASE_URL = "https://anypoint.mulesoft.com";
    private static final String TREBLLE_API_DISCOVERY_URL = "https://autodiscovery.treblle.com/api/v1/mulesoft";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(RegisterAPIClient.class);

    public RegisterAPIClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        logger.info("Initializing RegisterAPIClient");
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

    public boolean sendApiDataToThirdParty(List<Map<String, Object>> apisWithPolicies, String apiKey)
            throws IOException, InterruptedException {

        boolean sentSuccessfully = false;

        if (apisWithPolicies == null || apisWithPolicies.isEmpty()) {
            logger.info("No API data to send to third party endpoint.");
            return true;
        }

        // Convert the data to JSON
        String jsonPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(apisWithPolicies);

        logger.info("Sending API data to third party endpoint: " + TREBLLE_API_DISCOVERY_URL);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(TREBLLE_API_DISCOVERY_URL))
                .header("Content-Type", "application/json")
                .header("User-Agent", "MuleSoft-API-Discovery/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

        // Add API key if provided
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("x-api-key", apiKey);
        }

        HttpRequest request = requestBuilder.build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Successfully sent API data to Treblle. Response code: " + response.statusCode());
                logger.debug("Response body: " + response.body());
                sentSuccessfully = true;
            } else {
                logger.error("Failed to send API data to Treblle. Response code: " + response.statusCode() +
                        ", Response body: " + response.body());
                throw new RuntimeException("Failed to send data to Treblle endpoint: " + response.statusCode() +
                        " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error sending API data to Treblle endpoint: " + e.getMessage(), e);
            throw e;
        }

        return sentSuccessfully;
    }

    public String[] discoverApiPolicies(String clientId, String clientSecret, String organizationId,
            String[] existingApiIds, String apiKey)
            throws IOException, InterruptedException {

        List<Map<String, Object>> apisWithPolicies = new ArrayList<>();
        String accessToken = getAccessToken(clientId, clientSecret);
        logger.debug("Access Token obtained successfully.");

        List<Map<String, String>> environments = getEnvironments(accessToken, organizationId);
        logger.debug("Found " + environments.size() + " environments.");

        for (Map<String, String> env : environments) {
            String envId = env.get("id");
            String envName = env.get("name");
            logger.debug("Processing environment: " + envName + " (ID: " + envId + ")");

            List<Map<String, String>> apis = getApis(accessToken, organizationId, envId);
            logger.debug("  Found " + apis.size() + " APIs in " + envName + ".");

            for (Map<String, String> api : apis) {

                if (existingApiIds != null && Arrays.asList(existingApiIds).contains(api.get("id"))) {
                    logger.info("Skipping existing API: " + api.get("assetId") + " (ID: " + api.get("id") + ")");
                    continue; // Skip if the API is already in the list of existing APIs
                }

                String apiId = api.get("id");
                String apiName = api.get("assetId");
                logger.debug("    Checking policies for API: " + apiName + " (ID: " + apiId + ")");

                List<Map<String, String>> policies = getPolicies(accessToken, organizationId, envId, apiId);

                for (Map<String, String> policy : policies) {
                    logger.info("      Found policy: " + policy.get("name") + " (ID: " + policy.get("id") + ")");
                }

                logger.debug(" API - " + apiName + "-" + envName);

                for (Map<String, String> policy : policies) {

                    if ("treblle-policy".equals(policy.get("assetId"))) {

                        Map<String, Object> apiEntry = new HashMap<>();
                        apiEntry.put("apiName", apiName);
                        apiEntry.put("apiId", apiId);
                        apiEntry.put("environmentName", envName);
                        apisWithPolicies.add(apiEntry);
                        break; // Break after finding the Treblle policy
                    }

                }
            }
        }

        logger.info("Discovered APIs with Policies: " + apisWithPolicies.size() + " - XXXXX");

        for (Map<String, Object> api : apisWithPolicies) {
            logger.info("API: " + api.get("apiName") + " (ID: " + api.get("apiId") + ") - XXXXX");
            logger.info("Environment: " + api.get("environmentName") + " (ID: " + api.get("environmentId")
                    + ")");
        }

        // Prepare the list of API IDs to return
        List<String> apiIds = new ArrayList<>();
        apiIds.addAll(Arrays.asList(existingApiIds));

        if (apisWithPolicies.size() > 0) {

            logger.debug("Sending discovered API data to third party endpoint.");
            if (!sendApiDataToThirdParty(apisWithPolicies, apiKey)) {
                logger.error("Failed to send API data to third party endpoint.");
            } else {
                logger.debug("API data sent successfully to third party endpoint.");
                for (Map<String, Object> apiEntry : apisWithPolicies) {
                    String apiId = (String) apiEntry.get("apiId");
                    apiIds.add(apiId);
                }
            }

        }

        return apiIds.toArray(new String[0]);
    }

    // Main method for testing outside Mule (optional)
    public static void main(String[] args) {
        String clientId = "change"; // Replace with your actual client ID
        String clientSecret = "change"; // Replace with your actual client secret
        String organizationId = "change"; // Replace with your actual organization ID
        String apiKey = "change"; // Replace with your actual API key

        RegisterAPIClient client = new RegisterAPIClient();
        String[] existingApiIds = { "2040d6246" }; // Replace with actual existing API IDs if needed
        try {
            String[] result = client.discoverApiPolicies(clientId, clientSecret, organizationId, existingApiIds, apiKey);
            System.out.println("\n--- Final API Policy Report ---");
            System.out.println(client.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}