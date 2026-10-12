# Hosty 🚀

A full-stack, self-serve cloud deployment platform. Connect a Git repository, push code, and automatically get live public static URLs on Amazon S3 and serverless API endpoints on AWS Lambda — with zero cold-rebuild instant rollbacks, ephemeral branch previews, and a dedicated developer CLI.

Built with **Spring Boot 4 (Java 21)**, **AWS SDK v2**, **Amazon DynamoDB**, **Amazon S3**, **AWS Lambda**, and the **Hosty CLI**.

---

## 🌟 Key Capabilities

- ⚡ **Auto-Build Pipeline**: Automatic framework detection (React, Vite, Vue, Next.js, static HTML) with isolated process execution.
- 🌐 **S3 Static Hosting**: Automatic asset publishing with intelligent MIME-type detection and public static website hosting.
- ⚡ **Serverless Functions on AWS Lambda**: Drop an `api/` or `functions/` folder into your repository — Hosty packages and deploys it to AWS Lambda with instant public Function URLs over HTTPS.
- 🔄 **Instant Zero-Rebuild Rollbacks**: Roll back any deployment in milliseconds without cloning git or re-running builds by cloning S3 asset prefixes.
- 🌿 **Branch Previews & Automatic Teardown**: Feature branches and Pull Requests deploy isolated preview URLs. Merging or closing a PR automatically tears down preview Lambda functions and S3 artifacts.
- 💻 **Developer CLI (`hosty`)**: Full-featured command-line interface for authentication, project management, real-time deployment tracking, and rollbacks.
- 💰 **100% Free Tier ($0 Spend)**: Designed exclusively for AWS Always-Free and Free-Tier allocations (DynamoDB `PAY_PER_REQUEST`, S3 static hosting, Lambda 1M requests/month).

---

## 🏗️ Architecture & Completed Phases

```
Developer / Git Push / CLI (`hosty`)
             │
             ▼
      [Hosty Backend] (Spring Boot 4 / Java 21)
             │
   ┌─────────┼───────────────────────────┐
   ▼         ▼                           ▼
[DynamoDB] [AWS S3 Bucket]        [AWS Lambda]
(Metadata) (Static Web Hosting)   (Serverless Functions)
```

| Phase | Feature | Status | Description |
| :--- | :--- | :---: | :--- |
| **Phase 1** | Core API & DynamoDB | ✅ Completed | DynamoDB persistence for Users, Projects, Deployments, Domains |
| **Phase 2** | Auth & Free Quotas | ✅ Completed | JWT auth, BCrypt passwords, user project quotas |
| **Phase 3** | Projects & Rate Limiting | ✅ Completed | Project CRUD, token-bucket hourly deployment rate limits |
| **Phase 4** | Build Engine & S3 | ✅ Completed | Sandboxed `npm ci && npm run build`, S3 multi-asset publishing |
| **Phase 5** | GitHub Webhooks | ✅ Completed | HMAC-SHA256 signature verification, branch detection |
| **Phase 6** | Serverless on AWS Lambda | ✅ Completed | Node.js `api/` bundling, Lambda Function URLs with dual permissions |
| **Phase 7** | Build Logs & Streaming | ✅ Completed | Real-time deployment logs, build status progression |
| **Phase 8** | Previews & Auto-Teardown | ✅ Completed | Ephemeral preview environments, automatic S3 & Lambda teardown on PR close |
| **Phase 9** | Instant Rollbacks | ✅ Completed | Zero-rebuild S3 prefix cloning, sub-second rollbacks |
| **Phase 10** | Developer CLI Tool (`hosty`) | ✅ Completed | Standalone Node.js CLI tool with auth, deployment polling & rollbacks |

---

## 🛠️ Prerequisites

- **Java 21 LTS** (OpenJDK 21+)
- **Node.js v20+** and **npm**
- **Git**
- **AWS CLI v2** configured (`aws configure`) with an AWS account in `ap-south-1` (or your preferred region)

---

## 🚀 One-Command AWS Setup ($0 Free Tier)

Hosty includes automated infrastructure provisioning scripts that set up the S3 bucket, IAM execution role, and DynamoDB tables configured for $0 free tier:

### Windows (PowerShell):
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-aws.ps1
```

### Linux / macOS (Bash):
```bash
chmod +x ./scripts/setup-aws.sh
./scripts/setup-aws.sh
```

### Test AWS Connection:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-aws-connection.ps1
```

Provisioned resources:
- **S3 Bucket**: `mini-vercel-artifacts-<accountId>` (Website hosting enabled, public read policy)
- **IAM Role**: `mini-vercel-lambda-exec` (`AWSLambdaBasicExecutionRole`)
- **DynamoDB Tables**: `mv-users`, `mv-projects`, `mv-deployments`, `mv-email-claims`, `mv-domains` (All `PAY_PER_REQUEST`)

---

## 💻 Running the Backend

### 1. Build the Application
```bash
./mvnw.cmd package -DskipTests
```
*(On Linux/macOS use `./mvnw package -DskipTests`)*

### 2. Run the Spring Boot Server
```bash
java -jar target/hosty-0.0.1-SNAPSHOT.jar
```

The server starts on `http://localhost:8080`.

---

## 🖥️ Hosty CLI Tool (`cli/`)

Hosty comes with a dedicated CLI located in `cli/`.

### Installation & Linking:
```bash
cd cli
npm link
```
*(Or invoke directly using `node cli/bin/hosty.js <command>`)*

### Available Commands:

#### 1. Authentication
```bash
# Interactive login
hosty login

# Login with flags
hosty login --email admin@example.com --password "SuperSecretPassword123!" --api-url http://localhost:8080

# Check current user session
hosty whoami

# Logout
hosty logout
```

#### 2. Project Management
```bash
# List all your projects
hosty projects list

# Create a project
hosty projects create --name my-site --repo https://github.com/user/my-repo --branch main

# Delete a project
hosty projects delete <projectId>
```

#### 3. Deployments & Live Status
```bash
# Trigger deployment and stream live build status & URLs
hosty deploy <projectId>

# Deploy a specific branch or commit
hosty deploy <projectId> --branch feature/login --commit a1b2c3d

# List past deployments
hosty deployments list <projectId>
```

#### 4. Instant Rollback (Zero-Rebuild)
```bash
hosty rollback <projectId> <targetDeploymentId>
```
Instantly points live traffic back to a known-good build by cloning S3 assets in milliseconds without git clone or re-compilation.

---

## ⚡ Serverless Functions (AWS Lambda)

To add serverless API endpoints to your site, simply create an `api/` directory in your repository:

```
my-project/
├── index.html
├── package.json
└── api/
    ├── package.json
    └── index.js
```

Example handler (`api/index.js`):
```javascript
export const handler = async (event) => {
  return {
    statusCode: 200,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: 'Hello from Hosty on AWS Lambda 🚀',
      method: event.requestContext?.http?.method || event.httpMethod,
      timestamp: new Date().toISOString()
    })
  };
};
```

During build, Hosty:
1. Detects `api/index.js`.
2. Packages dependencies into a ZIP bundle.
3. Provisions or updates an AWS Lambda function (`mv-<projectId>`).
4. Creates a public Function URL (`AuthType: NONE`) with dual IAM invocation policies.
5. Surfaces the live serverless HTTPS endpoint in deployment output.

---

## 🧪 Comprehensive Automated Testing

Hosty maintains a strict test-driven development workflow with **75 automated tests** across the platform:

### 1. Spring Boot Backend Tests (62 JUnit 5 Tests)
```bash
./mvnw.cmd test
```
Covers:
- **`JwtServiceTest`**: Token generation, tampering, and expiration validation.
- **`QuotaServiceTest`**: Free-tier project limit enforcement.
- **`ProjectServiceTest`**: Project CRUD and tenant isolation.
- **`RateLimitServiceTest`**: Hourly and concurrent deployment limits.
- **`LocalBuildRunnerTest`**: Command injection prevention and timeout isolation.
- **`GitHubPushEventTest` & `GitHubPullRequestEventTest`**: Webhook HMAC signatures and payload parsing.
- **`DeploymentEnvironmentTest`**: Production vs preview branch routing.
- **`LambdaFunctionDeployerTest`**: Function packaging, handler resolution, and dual IAM policies.
- **`DeploymentServiceTeardownTest`**: Ephemeral preview cleanup on PR close.
- **`DeploymentRollbackTest`**: S3 prefix cloning and instant rollback guarantees.
- **`DomainServiceTest` & `AcmCertificateProvisionerTest`**: ACM TLS certificates and Route53 DNS verification.

### 2. CLI Tool Tests (13 Node.js Tests)
```bash
cd cli
npm.cmd test
```
Covers:
- Configuration persistence (`~/.hosty/config.json`) and environment variable overrides.
- HTTP REST API client with mock server verification.
- Error handling and HTTP status propagation.
- **End-to-End Live Backend Integration**: Live CLI login, `whoami`, project inspection, deployment listing, and instant rollback verification against `http://localhost:8080`.

---

## 📡 REST API Reference

| Endpoint | Method | Auth | Description |
| :--- | :---: | :---: | :--- |
| `/api/auth/register` | `POST` | Public | Register new user account |
| `/api/auth/login` | `POST` | Public | Authenticate and obtain JWT Bearer token |
| `/api/projects` | `GET` | Bearer | List user's projects |
| `/api/projects` | `POST` | Bearer | Create a new project |
| `/api/projects/{id}` | `GET` | Bearer | Get project details |
| `/api/projects/{id}` | `DELETE` | Bearer | Delete project |
| `/api/projects/{id}/deployments` | `POST` | Bearer | Trigger manual build & deployment |
| `/api/projects/{id}/deployments` | `GET` | Bearer | List project deployments |
| `/api/projects/{id}/deployments/{depId}` | `GET` | Bearer | Get deployment status, URLs, and logs |
| `/api/projects/{id}/deployments/{depId}/rollback` | `POST` | Bearer | **Instant zero-rebuild rollback** |
| `/api/projects/{id}/deployments/{depId}/cancel` | `POST` | Bearer | Cancel in-flight deployment |
| `/api/webhooks/github` | `POST` | HMAC | GitHub Push & PR webhook handler |

---

## 🛡️ License

MIT License. Built as an open cloud deployment architecture showcase.
