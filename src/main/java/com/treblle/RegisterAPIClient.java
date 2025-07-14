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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterAPIClient {

    private static final String ANYPOINT_BASE_URL = "https://anypoint.mulesoft.com";
    private static final String TREBLLE_API_DISCOVERY_URL = "https://autodiscovery.treblle.com/api/v1/mulesoft";
    private static final String TREBLLE_POLICY_NAME = "treblle-policy";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(RegisterAPIClient.class);

    /**
     * Constructs a new RegisterAPIClient instance.
     * Initializes the HTTP client and JSON object mapper for API communication.
     */
    public RegisterAPIClient() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Obtains an access token from the Anypoint Platform using client credentials.
     * 
     * @param clientId     The client ID for the connected app
     * @param clientSecret The client secret for the connected app
     * @return Access token for API authentication
     * @throws IOException          If there's an I/O error during the HTTP request
     * @throws InterruptedException If the request is interrupted
     * @throws RuntimeException     If authentication fails or returns non-200
     *                              status
     */
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
            logger.error("Failed to get access token: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException(
                    "Failed to get access token: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Retrieves all environments for a given organization from Anypoint Platform.
     * 
     * @param accessToken    Bearer token for API authentication
     * @param organizationId The organization/business group ID
     * @return List of environment maps containing 'id' and 'name' keys
     * @throws IOException          If there's an I/O error during the HTTP request
     * @throws InterruptedException If the request is interrupted
     * @throws RuntimeException     If the request fails or returns non-200 status
     */
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
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode envNode : dataNode) {
                    Map<String, String> env = new HashMap<>();

                    // Debug logging for missing environment fields
                    if (envNode.get("id") == null) {
                        logger.debug("Environment node missing 'id' field: {}", envNode);
                    }
                    if (envNode.get("name") == null) {
                        logger.debug("Environment node missing 'name' field: {}", envNode);
                    }

                    env.put("id", envNode.get("id") != null ? envNode.get("id").asText() : "");
                    env.put("name", envNode.get("name") != null ? envNode.get("name").asText() : "");
                    environments.add(env);
                }
            } else {
                logger.debug("No 'data' array found in environments response or data is null. Response: {}",
                        response.body());
            }
            return environments;
        } else {
            logger.error("Failed to get environments: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException(
                    "Failed to get environments: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Retrieves all APIs from a specific environment with pagination support.
     * Fetches APIs in batches of 20 to handle large datasets efficiently.
     * 
     * @param accessToken    Bearer token for API authentication
     * @param organizationId The organization/business group ID
     * @param environmentId  The environment ID to query APIs from
     * @return List of API maps containing 'id' and 'assetId' keys
     * @throws IOException          If there's an I/O error during the HTTP request
     * @throws InterruptedException If the request is interrupted
     * @throws RuntimeException     If the request fails or returns non-200 status
     */
    public List<Map<String, String>> getApis(String accessToken, String organizationId, String environmentId)
            throws IOException, InterruptedException {
        List<Map<String, String>> allApis = new ArrayList<>();
        int limit = 20;
        int offset = 0;
        boolean hasMorePages = true;

        while (hasMorePages) {
            String url = ANYPOINT_BASE_URL + "/apimanager/api/v1/organizations/" + organizationId + "/environments/"
                    + environmentId + "/apis?limit=" + limit + "&offset=" + offset;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = objectMapper.readTree(response.body());
                List<Map<String, String>> pageApis = new ArrayList<>();

                JsonNode dataNodeAssets = rootNode.get("assets");
                if (dataNodeAssets != null && dataNodeAssets.isArray()) {
                    for (JsonNode assetNode : dataNodeAssets) {
                        JsonNode dataNode = assetNode.get("apis");
                        if (dataNode != null && dataNode.isArray()) {
                            for (JsonNode apiNode : dataNode) {
                                Map<String, String> api = new HashMap<>();

                                // Debug logging for missing API fields
                                if (apiNode.get("id") == null) {
                                    logger.debug("API node missing 'id' field: {}", apiNode);
                                }
                                if (apiNode.get("assetId") == null) {
                                    logger.debug("API node missing 'assetId' field: {}", apiNode);
                                }

                                api.put("id", apiNode.get("id") != null ? apiNode.get("id").asText() : "");
                                api.put("assetId",
                                        apiNode.get("assetId") != null ? apiNode.get("assetId").asText() : "");
                                pageApis.add(api);
                            }
                        } else {
                            logger.debug("Asset node missing 'apis' array or apis is null: {}", assetNode);
                        }
                    }
                } else {
                    logger.debug(
                            "No 'assets' array found in API response or assets is null for environment {}. Response: {}",
                            environmentId, response.body());
                }

                allApis.addAll(pageApis);

                // Check if there are more pages
                if (pageApis.size() < limit) {
                    hasMorePages = false;
                } else {
                    offset += limit;
                    logger.debug("Retrieved {} APIs so far, fetching next page...", allApis.size());
                }
            } else {
                logger.error("Failed to get APIs for environment {}: {} - {}", environmentId, response.statusCode(),
                        response.body());
                throw new RuntimeException("Failed to get APIs for environment " + environmentId + ": "
                        + response.statusCode() + " - " + response.body());
            }
        }

        logger.debug("Retrieved total of {} APIs for environment {}", allApis.size(), environmentId);
        return allApis;
    }

    /**
     * Retrieves all policies applied to a specific API.
     * 
     * @param accessToken    Bearer token for API authentication
     * @param organizationId The organization/business group ID
     * @param environmentId  The environment ID where the API is located
     * @param apiId          The specific API ID to get policies for
     * @return List of policy maps containing policy details including 'id', 'name',
     *         'policyTemplateId', and 'assetId'
     * @throws IOException          If there's an I/O error during the HTTP request
     * @throws InterruptedException If the request is interrupted
     * @throws RuntimeException     If the request fails or returns non-200 status
     */
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
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode policyNode : dataNode) {
                    Map<String, String> policy = new HashMap<>();

                    // Debug logging for missing policy fields
                    if (policyNode.get("policyId") == null) {
                        logger.debug("Policy node missing 'policyId' field: {}", policyNode);
                    }
                    if (policyNode.get("type") == null) {
                        logger.debug("Policy node missing 'type' field: {}", policyNode);
                    }
                    if (policyNode.get("policyTemplateId") == null) {
                        logger.debug("Policy node missing 'policyTemplateId' field: {}", policyNode);
                    }

                    policy.put("id", policyNode.get("policyId") != null ? policyNode.get("policyId").asText() : "");
                    policy.put("name", policyNode.get("type") != null ? policyNode.get("type").asText() : "");
                    policy.put("policyTemplateId",
                            policyNode.get("policyTemplateId") != null ? policyNode.get("policyTemplateId").asText()
                                    : "");

                    // Extract assetId from the template node
                    JsonNode templateNode = policyNode.get("template");
                    if (templateNode != null && templateNode.has("assetId")) {
                        policy.put("assetId", templateNode.get("assetId").asText());
                    } else {
                        logger.debug("Policy template node missing 'assetId' field for policy {}. Template: {}",
                                policyNode.get("policyId"), templateNode);
                        policy.put("assetId", "");
                    }

                    policies.add(policy);
                }
            } else {
                logger.debug(
                        "No 'policies' array found in policies response or policies is null for API {}. Response: {}",
                        apiId, response.body());
            }
            return policies;
        } else {
            logger.error("Failed to get policies for API {}: {} - {}", apiId, response.statusCode(), response.body());
            throw new RuntimeException(
                    "Failed to get policies for API " + apiId + ": " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Checks for automated Treblle policy in a specific environment and returns its
     * rule of application.
     * 
     * @param accessToken    Bearer token for API authentication
     * @param organizationId The organization/business group ID
     * @param environmentId  The environment ID to check for automated policies
     * @return Map containing the rule of application details (range, javaVersions,
     *         technologies)
     *         or null if no Treblle policy found, empty map if policy exists but no
     *         rule content
     * @throws IOException          If there's an I/O error during the HTTP request
     * @throws InterruptedException If the request is interrupted
     * @throws RuntimeException     If the request fails or returns non-200 status
     */
    public Map<String, Object> getAutomatedTrebllePolicy(String accessToken, String organizationId,
            String environmentId)
            throws IOException, InterruptedException {

        String url = ANYPOINT_BASE_URL + "/apimanager/api/v1/organizations/" + organizationId
                + "/automated-policies?environmentId=" + environmentId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode automatedPoliciesNode = rootNode.get("automatedPolicies");
            if (automatedPoliciesNode != null && automatedPoliciesNode.isArray()) {
                logger.debug("Found {} automated policies in environment {}", automatedPoliciesNode.size(),
                        environmentId);
                for (JsonNode policyNode : automatedPoliciesNode) {
                    JsonNode assetIdNode = policyNode.get("assetId");
                    if (assetIdNode != null && TREBLLE_POLICY_NAME.equals(assetIdNode.asText())) {
                        logger.info("Found automated treblle-policy in environment: " + environmentId);

                        // Extract ruleOfApplication content
                        JsonNode ruleOfApplicationNode = policyNode.get("ruleOfApplication");
                        if (ruleOfApplicationNode != null) {
                            Map<String, Object> ruleOfApplication = new HashMap<>();

                            // Extract range information
                            JsonNode rangeNode = ruleOfApplicationNode.get("range");
                            if (rangeNode != null) {
                                Map<String, String> range = new HashMap<>();
                                if (rangeNode.has("from")) {
                                    range.put("from", rangeNode.get("from").asText());
                                } else {
                                    logger.debug("Range node missing 'from' field in treblle-policy for environment {}",
                                            environmentId);
                                }
                                if (rangeNode.has("to")) {
                                    range.put("to", rangeNode.get("to").asText());
                                } else {
                                    logger.debug("Range node missing 'to' field in treblle-policy for environment {}",
                                            environmentId);
                                }
                                ruleOfApplication.put("range", range);
                            } else {
                                logger.debug(
                                        "Rule of application missing 'range' field in treblle-policy for environment {}",
                                        environmentId);
                            }

                            // Extract javaVersions
                            JsonNode javaVersionsNode = ruleOfApplicationNode.get("javaVersions");
                            if (javaVersionsNode != null && javaVersionsNode.isArray()) {
                                List<String> javaVersions = new ArrayList<>();
                                for (JsonNode versionNode : javaVersionsNode) {
                                    javaVersions.add(versionNode.asText());
                                }
                                ruleOfApplication.put("javaVersions", javaVersions);
                            } else {
                                logger.debug(
                                        "Rule of application missing 'javaVersions' array in treblle-policy for environment {}",
                                        environmentId);
                            }

                            // Extract technologies
                            JsonNode technologiesNode = ruleOfApplicationNode.get("technologies");
                            if (technologiesNode != null && technologiesNode.isArray()) {
                                List<String> technologies = new ArrayList<>();
                                for (JsonNode techNode : technologiesNode) {
                                    technologies.add(techNode.asText());
                                }
                                ruleOfApplication.put("technologies", technologies);
                            } else {
                                logger.debug(
                                        "Rule of application missing 'technologies' array in treblle-policy for environment {}",
                                        environmentId);
                            }

                            logger.debug("Successfully extracted rule of application: {}", ruleOfApplication);
                            return ruleOfApplication;
                        } else {
                            logger.warn("Found treblle-policy but no ruleOfApplication content in environment: "
                                    + environmentId);
                            return new HashMap<>(); // Return empty map if no ruleOfApplication
                        }
                    } else {
                        logger.debug("Policy with assetId '{}' is not a treblle-policy in environment {}",
                                assetIdNode != null ? assetIdNode.asText() : "null", environmentId);
                    }
                }
            } else {
                logger.debug(
                        "No 'automatedPolicies' array found in response or automatedPolicies is null for environment {}. Response: {}",
                        environmentId, response.body());
            }
            logger.debug("No automated treblle-policy found in environment: " + environmentId);
            return null;
        } else {
            logger.error("Failed to get automated policies for environment {}: {} - {}", environmentId,
                    response.statusCode(), response.body());
            throw new RuntimeException("Failed to get automated policies for environment " + environmentId + ": "
                    + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Sends API data to third-party endpoint in batches of 20 entries each.
     * Handles large datasets by breaking them into manageable chunks for better
     * performance and reliability.
     * 
     * @param apisWithPolicies List of API data maps to send to the endpoint
     * @param apiKey           API key for authentication with the third-party
     *                         endpoint (optional)
     * @return true if all batches were sent successfully, false if any batch failed
     * @throws IOException          If there's an I/O error during the HTTP request
     * @throws InterruptedException If the request is interrupted
     */
    public boolean sendApiDataToThirdParty(List<Map<String, Object>> apisWithPolicies, String apiKey)
            throws IOException, InterruptedException {

        if (apisWithPolicies == null || apisWithPolicies.isEmpty()) {
            logger.debug("No API data to send to third party endpoint.");
            return true;
        }

        int batchSize = 20;
        int totalEntries = apisWithPolicies.size();
        int totalBatches = (int) Math.ceil((double) totalEntries / batchSize);
        boolean allBatchesSuccessful = true;

        logger.info("Sending {} API entries in {} batches of {} to third party endpoint: {}",
                totalEntries, totalBatches, batchSize, TREBLLE_API_DISCOVERY_URL);

        for (int i = 0; i < totalEntries; i += batchSize) {
            int endIndex = Math.min(i + batchSize, totalEntries);
            List<Map<String, Object>> batch = apisWithPolicies.subList(i, endIndex);
            int batchNumber = (i / batchSize) + 1;

            logger.debug("Sending batch {} of {} ({} entries)", batchNumber, totalBatches, batch.size());

            try {
                boolean batchResult = sendBatchToThirdParty(batch, apiKey, batchNumber);
                if (!batchResult) {
                    allBatchesSuccessful = false;
                    logger.error("Failed to send batch {} of {}", batchNumber, totalBatches);
                } else {
                    logger.debug("Successfully sent batch {} of {}", batchNumber, totalBatches);
                }
            } catch (Exception e) {
                logger.error("Error sending batch {} of {}: {}", batchNumber, totalBatches, e.getMessage(), e);
                allBatchesSuccessful = false;
            }
        }

        if (allBatchesSuccessful) {
            logger.info("All {} batches sent successfully to Treblle", totalBatches);
        } else {
            logger.error("One or more batches failed to send to Treblle");
        }

        return allBatchesSuccessful;
    }

    /**
     * Sends a single batch of API data to the third-party endpoint.
     * Helper method for batch processing of API data.
     * 
     * @param batch       List of API data maps for this specific batch
     * @param apiKey      API key for authentication with the third-party endpoint
     *                    (optional)
     * @param batchNumber The batch number for logging purposes
     * @return true if the batch was sent successfully, false otherwise
     * @throws IOException          If there's an I/O error during the HTTP request
     * @throws InterruptedException If the request is interrupted
     */
    private boolean sendBatchToThirdParty(List<Map<String, Object>> batch, String apiKey, int batchNumber)
            throws IOException, InterruptedException {

        // Convert the batch data to JSON
        String jsonPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(batch);

        logger.debug("Batch {} payload: {}", batchNumber, jsonPayload);

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
                logger.debug("Batch {} sent successfully. Response code: {}", batchNumber, response.statusCode());
                logger.debug("Batch {} response body: {}", batchNumber, response.body());
                return true;
            } else {
                logger.error("Failed to send batch {}. Response code: {}, Response body: {}",
                        batchNumber, response.statusCode(), response.body());
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error sending batch {} to Treblle endpoint: {}", batchNumber, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Main method to discover APIs with Treblle policies across multiple
     * organizations.
     * Processes both automated policies and individual API policies, verifies rule
     * compliance,
     * and sends discovered API data to the third-party endpoint.
     * 
     * @param clientId        The client ID for Anypoint Platform authentication
     * @param clientSecret    The client secret for Anypoint Platform authentication
     * @param organizationIds Comma-separated list of organization IDs to process
     * @param apiKey          API key for the third-party endpoint (optional)
     * @return Array of API IDs that were successfully processed and sent
     * @throws IOException          If there's an I/O error during HTTP requests
     * @throws InterruptedException If any request is interrupted
     */
    public String[] discoverApiPolicies(String clientId, String clientSecret, String organizationIds, String apiKey)
            throws IOException, InterruptedException {

        List<Map<String, Object>> apisWithPolicies = new ArrayList<>();

        String accessToken = getAccessToken(clientId, clientSecret);
        logger.debug("Access Token obtained successfully.");

        String[] businessGroupIds = organizationIds.split(",");
        logger.debug("Discovering APIs for organizations: {}", Arrays.toString(businessGroupIds));

        for (String businessgrp : businessGroupIds) {

            String organizationId = businessgrp.trim();
            List<Map<String, String>> environments = getEnvironments(accessToken, organizationId);
            logger.debug("Found {} environments.", environments.size());

            String orgName = getBusinessGroupName(accessToken, organizationId);
            if (orgName == null) {
                logger.debug("Organization name is null for organization ID: {}", organizationId);
                orgName = "Unknown Organization";
            }
            logger.debug("Organization name for {}: {}", organizationId, orgName);

            for (Map<String, String> env : environments) {

                String envId = env.get("id");
                String envName = env.get("name");

                if (envId == null || envId.isEmpty()) {
                    logger.debug("Environment ID is null or empty for environment: {}", env);
                    continue;
                }
                if (envName == null || envName.isEmpty()) {
                    logger.debug("Environment name is null or empty for environment: {}", env);
                    envName = "Unknown Environment";
                }

                logger.info("Processing environment: {} (ID: {})", envName, envId);

                List<Map<String, String>> apis = getApis(accessToken, organizationId, envId);
                logger.debug("Found {} APIs in {}.", apis.size(), envName);

                Map<String, Object> automatedPolicyRule = getAutomatedTrebllePolicy(accessToken, organizationId, envId);
                if (automatedPolicyRule != null) {
                    logger.info("Found automated treblle-policy with rule in environment: {}", envName);
                    logger.debug("Automated policy rule details: {}", automatedPolicyRule);
                    for (Map<String, String> api : apis) {
                        processApi(api, envName, envId, orgName, organizationId, automatedPolicyRule, accessToken,
                                apisWithPolicies);
                    }
                } else {
                    for (Map<String, String> api : apis) {
                        String apiId = api.get("id");
                        String apiName = api.get("assetId");

                        if (apiId == null || apiId.isEmpty()) {
                            logger.debug("API ID is null or empty for API: {}", api);
                            continue;
                        }
                        if (apiName == null || apiName.isEmpty()) {
                            logger.debug("API name is null or empty for API: {}", api);
                            apiName = "Unknown API";
                        }

                        logger.debug("Checking policies for API: {} (ID: {})", apiName, apiId);

                        List<Map<String, String>> policies = getPolicies(accessToken, organizationId, envId, apiId);

                        // Check for treblle-policy without logging each policy
                        boolean treblleFound = false;
                        for (Map<String, String> policy : policies) {
                            if (TREBLLE_POLICY_NAME.equals(policy.get("assetId"))) {
                                logger.debug("Found treblle-policy for API: {} (ID: {})", apiName, apiId);
                                treblleFound = true;
                                break;
                            }
                        }

                        if (treblleFound) {
                            addApiEntry(apiName, apiId, envName, envId, orgName, organizationId, apisWithPolicies);
                        }
                    }
                }
            }
        }

        logger.info("API Discovery Summary: {} APIs with Treblle policies found across {} organizations",
                apisWithPolicies.size(), businessGroupIds.length);

        // Log a summary instead of individual APIs
        if (apisWithPolicies.size() > 0) {
            logger.debug("Summary of discovered APIs:");
            for (Map<String, Object> api : apisWithPolicies) {
                logger.debug("API: {} in environment: {} (org: {})",
                        api.get("apiName"), api.get("environmentName"), api.get("orgName"));
            }
        }

        List<String> apiIds = new ArrayList<>();

        if (!apisWithPolicies.isEmpty()) {
            logger.debug("Sending discovered API data to third party endpoint.");
            if (!sendApiDataToThirdParty(apisWithPolicies, apiKey)) {
                logger.error("Failed to send API data to third party endpoint.");
            } else {
                logger.debug("API data sent successfully to third party endpoint.");
                for (Map<String, Object> apiEntry : apisWithPolicies) {
                    apiIds.add((String) apiEntry.get("apiId"));
                }
            }
        }

        return apiIds.toArray(new String[0]);
    }

    /**
     * Processes an API by verifying it against automated policy rules and adding it
     * to the collection if compliant.
     * Fetches detailed API information and validates against rule of application
     * criteria.
     * 
     * @param api                 Map containing basic API information (id, assetId)
     * @param envName             The environment name for logging
     * @param envId               The environment ID
     * @param orgName             The organization name
     * @param orgId               The organization ID
     * @param automatedPolicyRule Map containing the automated policy rule criteria
     * @param accessToken         Bearer token for API authentication
     * @param apisWithPolicies    List to add compliant APIs to
     */
    private void processApi(Map<String, String> api, String envName, String envId, String orgName, String orgId,
            Map<String, Object> automatedPolicyRule, String accessToken, List<Map<String, Object>> apisWithPolicies) {

        String apiId = api.get("id");
        String apiName = api.get("assetId");

        logger.debug("API - {}-{}", apiName, envName);

        try {
            // Get API details from the REST API
            Map<String, Object> apiDetails = getApiDetails(accessToken, orgId, envId, apiId);
            logger.debug("Retrieved API details for {}: {}", apiName, apiDetails);

            // Verify rule of application
            boolean ruleVerified = verifyRuleOfApplication(apiDetails, automatedPolicyRule);

            if (ruleVerified) {
                logger.debug("API {} passed rule verification, adding to list", apiName);
                addApiEntry(apiName, apiId, envName, envId, orgName, orgId, apisWithPolicies);
            } else {
                logger.warn("API {} failed rule verification, skipping", apiName);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Error processing API {}: {}", apiName, e.getMessage());
            logger.debug("Error details for API {}: ", apiName, e);
            // Add API without verification if there's an error
            logger.debug("Adding API {} without verification due to error", apiName);
            addApiEntry(apiName, apiId, envName, envId, orgName, orgId, apisWithPolicies);
        } catch (Exception e) {
            logger.error("Unexpected error processing API {}: {}", apiName, e.getMessage());
            logger.debug("Unexpected error details for API {}: ", apiName, e);
            // Add API without verification if there's an unexpected error
            logger.debug("Adding API {} without verification due to unexpected error", apiName);
            addApiEntry(apiName, apiId, envName, envId, orgName, orgId, apisWithPolicies);
        }
    }

    /**
     * Creates and adds a new API entry to the collection with all relevant
     * metadata.
     * 
     * @param apiName          The API asset name
     * @param apiId            The unique API identifier
     * @param envName          The environment name
     * @param envId            The environment ID
     * @param orgName          The organization name
     * @param orgId            The organization ID
     * @param apisWithPolicies List to add the new API entry to
     */
    private void addApiEntry(String apiName, String apiId, String envName, String envId, String orgName,
            String orgId, List<Map<String, Object>> apisWithPolicies) {

        Map<String, Object> apiEntry = new HashMap<>();
        apiEntry.put("apiName", apiName != null ? apiName : "");
        apiEntry.put("apiId", apiId != null ? apiId : "");
        apiEntry.put("environmentName", envName != null ? envName : "");
        apiEntry.put("environmentId", envId != null ? envId : "");
        apiEntry.put("orgName", orgName != null ? orgName : "");
        apiEntry.put("orgId", orgId != null ? orgId : "");

        // Debug logging for null values
        if (apiName == null)
            logger.debug("API name is null when adding API entry");
        if (apiId == null)
            logger.debug("API ID is null when adding API entry");
        if (envName == null)
            logger.debug("Environment name is null when adding API entry");
        if (envId == null)
            logger.debug("Environment ID is null when adding API entry");
        if (orgName == null)
            logger.debug("Organization name is null when adding API entry");
        if (orgId == null)
            logger.debug("Organization ID is null when adding API entry");

        logger.debug("Adding API entry: {}", apiEntry);
        apisWithPolicies.add(apiEntry);
    }

    /**
     * Retrieves the business group name for a given organization ID.
     * 
     * @param accessToken    Bearer token for API authentication
     * @param organizationId The organization/business group ID
     * @return The business group name or null if not found
     * @throws IOException          If there's an I/O error during the HTTP request
     * @throws InterruptedException If the request is interrupted
     * @throws RuntimeException     If the request fails or returns non-200 status
     */
    public String getBusinessGroupName(String accessToken, String organizationId)
            throws IOException, InterruptedException {
        String url = ANYPOINT_BASE_URL + "/accounts/api/organizations/" + organizationId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode nameNode = rootNode.get("name");
            if (nameNode != null && !nameNode.isNull()) {
                return nameNode.asText();
            } else {
                logger.warn("Business group name not found in response for organization {}. Response: {}",
                        organizationId, response.body());
                return null;
            }
        } else {
            logger.error("Failed to get business group name for organization {}: {} - {}",
                    organizationId, response.statusCode(), response.body());
            throw new RuntimeException("Failed to get business group name for organization " + organizationId + ": "
                    + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Retrieves detailed information about a specific API including gateway version
     * and runtime metadata.
     * Used for verifying API compliance against automated policy rules.
     * 
     * @param accessToken    Bearer token for API authentication
     * @param organizationId The organization/business group ID
     * @param environmentId  The environment ID where the API is located
     * @param apiId          The specific API ID to get details for
     * @return Map containing API details including apiGatewayVersion and
     *         javaVersion
     * @throws IOException          If there's an I/O error during the HTTP request
     * @throws InterruptedException If the request is interrupted
     * @throws RuntimeException     If the request fails or returns non-200 status
     */
    public Map<String, Object> getApiDetails(String accessToken, String organizationId, String environmentId,
            String apiId)
            throws IOException, InterruptedException {
        String url = ANYPOINT_BASE_URL + "/apimanager/api/v1/organizations/" + organizationId + "/environments/"
                + environmentId + "/apis/" + apiId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode rootNode = objectMapper.readTree(response.body());
            Map<String, Object> apiDetails = new HashMap<>();

            // Extract endpoint runtime metadata
            JsonNode endpointNode = rootNode.get("endpoint");
            if (endpointNode != null) {
                logger.debug("Found endpoint node for API {}: {}", apiId, endpointNode);

                if (endpointNode.has("apiGatewayVersion") && !endpointNode.get("apiGatewayVersion").isNull()) {
                    apiDetails.put("apiGatewayVersion", endpointNode.get("apiGatewayVersion").asText());
                    logger.debug("API {} has apiGatewayVersion: {}", apiId,
                            endpointNode.get("apiGatewayVersion").asText());
                } else {
                    logger.debug("API {} missing or null 'apiGatewayVersion' field in endpoint node", apiId);
                }

                JsonNode runtimeMetadataNode = endpointNode.get("runtimeMetadata");
                if (runtimeMetadataNode != null) {
                    if (runtimeMetadataNode.has("javaVersion") && !runtimeMetadataNode.get("javaVersion").isNull()) {
                        apiDetails.put("javaVersion", runtimeMetadataNode.get("javaVersion").asText());
                        logger.debug("API {} has javaVersion: {}", apiId,
                                runtimeMetadataNode.get("javaVersion").asText());
                    } else {
                        logger.debug("API {} missing or null 'javaVersion' field in runtimeMetadata", apiId);
                    }
                } else {
                    logger.debug("API {} missing 'runtimeMetadata' node in endpoint", apiId);
                }
            } else {
                logger.debug("API {} missing 'endpoint' node. Response: {}", apiId, response.body());
            }

            logger.debug("Extracted API details for {}: {}", apiId, apiDetails);
            return apiDetails;
        } else {
            logger.error("Failed to get API details for API {}: {} - {}", apiId, response.statusCode(),
                    response.body());
            throw new RuntimeException("Failed to get API details for API " + apiId + ": "
                    + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Verifies if an API complies with the automated policy rule of application.
     * Checks Java version compatibility and gateway version range compliance.
     * 
     * @param apiDetails          Map containing API technical details (javaVersion,
     *                            apiGatewayVersion)
     * @param automatedPolicyRule Map containing policy rule criteria (javaVersions,
     *                            range)
     * @return true if the API passes all rule verifications, false otherwise
     */
    private boolean verifyRuleOfApplication(Map<String, Object> apiDetails, Map<String, Object> automatedPolicyRule) {
        if (automatedPolicyRule == null || automatedPolicyRule.isEmpty()) {
            logger.debug("No automated policy rule to verify against");
            return true; // No rule to verify against
        }

        logger.debug("Verifying API details against automated policy rule");
        logger.debug("API Details: {}", apiDetails);
        logger.debug("Policy Rule: {}", automatedPolicyRule);

        // Check Java version compatibility
        if (automatedPolicyRule.containsKey("javaVersions")) {
            @SuppressWarnings("unchecked")
            List<String> supportedJavaVersions = (List<String>) automatedPolicyRule.get("javaVersions");
            String apiJavaVersion = (String) apiDetails.get("javaVersion");

            logger.debug("Checking Java version compatibility. API version: '{}', Supported versions: {}",
                    apiJavaVersion, supportedJavaVersions);

            if (apiJavaVersion != null && supportedJavaVersions != null && !supportedJavaVersions.isEmpty()) {
                if (!supportedJavaVersions.contains(apiJavaVersion)) {
                    logger.warn("API Java version '{}' not supported by automated policy. Supported: {}",
                            apiJavaVersion, supportedJavaVersions);
                    return false;
                }
                logger.debug("Java version '{}' is supported", apiJavaVersion);
            } else {
                if (apiJavaVersion == null) {
                    logger.debug("API Java version is null, skipping Java version verification");
                }
                if (supportedJavaVersions == null || supportedJavaVersions.isEmpty()) {
                    logger.debug("No supported Java versions specified in policy rule");
                }
            }
        } else {
            logger.debug("No Java version requirements specified in automated policy rule");
        }

        // Check version range compatibility
        if (automatedPolicyRule.containsKey("range")) {
            @SuppressWarnings("unchecked")
            Map<String, String> versionRange = (Map<String, String>) automatedPolicyRule.get("range");
            String apiVersion = (String) apiDetails.get("apiGatewayVersion");

            logger.debug("Checking version range compatibility. API version: '{}', Version range: {}",
                    apiVersion, versionRange);

            if (apiVersion != null && versionRange != null && !versionRange.isEmpty()) {
                String fromVersion = versionRange.get("from");
                String toVersion = versionRange.get("to");

                if (fromVersion != null || toVersion != null) {
                    boolean versionInRange = isVersionInRange(apiVersion, fromVersion, toVersion);
                    if (!versionInRange) {
                        logger.warn("API version '{}' not in supported range [{} - {}]",
                                apiVersion, fromVersion, toVersion);
                        return false;
                    }
                    logger.debug("Version '{}' is in supported range", apiVersion);
                } else {
                    logger.debug("Version range contains no 'from' or 'to' values: {}", versionRange);
                }
            } else {
                if (apiVersion == null) {
                    logger.debug("API version is null, failing version range check");
                    return false;
                }
                if (versionRange == null || versionRange.isEmpty()) {
                    logger.debug("No version range specified in policy rule or range is empty");
                }
            }
        } else {
            logger.debug("No version range requirements specified in automated policy rule");
        }

        logger.debug("API passed all automated policy rule verifications");
        return true;
    }

    /**
     * Checks if a version falls within the specified range (inclusive).
     * Helper method for version range validation in policy rules.
     * 
     * @param version     The version to check
     * @param fromVersion The minimum version (inclusive), can be null
     * @param toVersion   The maximum version (inclusive), can be null
     * @return true if the version is within the specified range, false otherwise
     */
    private boolean isVersionInRange(String version, String fromVersion, String toVersion) {
        // Simple version comparison - you might want to use a more sophisticated
        // version comparison library
        try {
            if (fromVersion != null && compareVersions(version, fromVersion) < 0) {
                return false;
            }
            if (toVersion != null && compareVersions(version, toVersion) > 0) {
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.warn("Error comparing versions: {}", e.getMessage());
            return true; // Default to true if comparison fails
        }
    }

    /**
     * Compares two semantic version strings.
     * Uses simple dot-separated numeric comparison (e.g., "4.1.1" vs "4.5.0").
     * 
     * @param version1 The first version string to compare
     * @param version2 The second version string to compare
     * @return negative integer if version1 < version2, zero if equal, positive if
     *         version1 > version2
     */
    private int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int part1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int part2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (part1 < part2)
                return -1;
            if (part1 > part2)
                return 1;
        }

        return 0;
    }

    /**
     * Main method for testing the RegisterAPIClient functionality outside of Mule
     * runtime.
     * Demonstrates how to use the client to discover APIs with Treblle policies.
     * 
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        String clientId = "change"; // Replace with your actual client ID
        String clientSecret = "change"; // Replace with your actual client secret
        String organizationId = "change: comma-separated list of organization IDs"; // Replace with your actual organization IDs
        String apiKey = "change"; // Replace with your actual API key

        RegisterAPIClient client = new RegisterAPIClient();
        try {
            String[] result = client.discoverApiPolicies(clientId, clientSecret, organizationId, apiKey);
            System.out.println("\n--- Final API Policy Report ---");
            System.out.println(client.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}