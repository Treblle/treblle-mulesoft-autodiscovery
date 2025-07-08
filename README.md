# Treblle Register APIs

This project is a Mule 4 application that connects to MuleSoft Anypoint Platform to discover APIs and their applied policies across all environments in a given organization. It uses a Java component to interact with Anypoint Platform REST APIs, fetches API and policy data, and logs a report at regular intervals.

## Features

- **Automated API Discovery:** Periodically fetches all APIs registered in all environments of a MuleSoft organization.
- **Policy Reporting:** Retrieves and logs all policies applied to each API.
- **Java Integration:** Uses a custom Java class (`RegisterAPIClient`) for Anypoint Platform API calls.
- **Configurable Credentials:** Uses externalized properties for client ID, client secret, and organization ID.

## How It Works

- A Mule flow (`api-policy-checker-flow`) is triggered by a scheduler (default: every 15 seconds).
- The flow instantiates the `RegisterAPIClient` Java class.
- The Java class:
  - Authenticates with Anypoint Platform using client credentials.
  - Fetches all environments for the organization.
  - For each environment, fetches all APIs.
  - For each API, fetches all applied policies.
- The results are logged for monitoring and auditing.

## Building and Running

1. **Build the project:**
   ```sh
   mvn clean install
   ```
2. **Deploy to Mule runtime:**  
   Deploy the packaged application (`.jar` or `.zip` in `target/`) to your Mule 4 runtime or Anypoint Runtime Manager.
