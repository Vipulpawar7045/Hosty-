# Hosty (Mini-Vercel) — Complete Project Knowledge Base

> **Purpose of this file**: Hand this to any AI agent or new developer. It contains everything needed to understand, modify, or extend the project without reading the source code.

---

## 1. What We Built

**Hosty** (codenamed _mini-vercel_) is a self-serve, Vercel-like cloud deployment platform. Users push code, and the system clones, builds, publishes static assets, optionally deploys serverless functions, and returns a live URL — all through a REST API and CLI.

### Core Value Proposition
- **Register → Create project → Deploy → Get a live URL** in minutes
- GitHub webhook auto-deploys on `git push`
- Production / Preview environments per branch
- Serverless functions (AWS Lambda) auto-detected and deployed alongside static sites
- Custom domain support with ACM SSL certificates
- Instant rollback (zero-rebuild, S3 copy)
- Rate limiting, quotas, and admin monitoring

### Tech Stack
| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 4.1 (WebMVC), Spring Security |
| **Database** | AWS DynamoDB (Enhanced Client, annotation-mapped entities) |
| **Object Storage** | AWS S3 |
| **Compute (serverless)** | AWS Lambda + Function URLs |
| **SSL / DNS** | AWS ACM (us-east-1) + Route53 |
| **Auth** | JWT (HS256, jjwt 0.13) + BCrypt passwords |
| **CLI** | Node.js 20+ (zero dependencies, native `fetch`) |
| **Build** | Maven (mvnw) |

---

## 2. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        CLIENTS                                          │
│   ┌────────────┐    ┌──────────────┐    ┌─────────────────────┐         │
│   │  Hosty CLI  │    │  curl / UI   │    │  GitHub Webhooks    │         │
│   └─────┬──────┘    └──────┬───────┘    └──────────┬──────────┘         │
│         │                  │                       │                    │
│         ▼                  ▼                       ▼                    │
│   ┌────────────────────────────────────────────────────────────────┐    │
│   │                  Spring Boot REST API (:8080)                  │    │
│   │  ┌──────────┐ ┌────────────┐ ┌────────────┐ ┌──────────────┐  │    │
│   │  │   Auth   │ │  Projects  │ │Deployments │ │   Domains    │  │    │
│   │  │Controller│ │ Controller │ │ Controller │ │  Controller  │  │    │
│   │  └────┬─────┘ └─────┬──────┘ └─────┬──────┘ └──────┬───────┘  │    │
│   │       │              │              │               │          │    │
│   │  ┌────▼─────┐ ┌──────▼──────┐ ┌────▼───────┐ ┌─────▼──────┐  │    │
│   │  │  User    │ │   Project   │ │ Deployment │ │  Domain    │  │    │
│   │  │ Service  │ │   Service   │ │  Service   │ │  Service   │  │    │
│   │  └────┬─────┘ └──────┬──────┘ └────┬───────┘ └─────┬──────┘  │    │
│   │       │              │              │               │          │    │
│   │       │              │         ┌────▼───────┐       │          │    │
│   │       │              │         │ BuildRunner│       │          │    │
│   │       │              │         │ (Local)    │       │          │    │
│   │       │              │         └────┬───────┘       │          │    │
│   │       │              │              │               │          │    │
│   │       │              │         ┌────▼───────┐  ┌────▼───────┐  │    │
│   │       │              │         │S3Publisher  │  │ACM/Route53│  │    │
│   │       │              │         └────┬───────┘  └────────────┘  │    │
│   │       │              │              │                          │    │
│   │       │              │         ┌────▼──────────────┐           │    │
│   │       │              │         │ LambdaDeployer    │           │    │
│   │       │              │         │ (Function URLs)   │           │    │
│   │       │              │         └───────────────────┘           │    │
│   └───────┼──────────────┼──────────────┼──────────────────────────┘    │
│           │              │              │                               │
│           ▼              ▼              ▼                               │
│   ┌──────────────────────────────────────────────────────┐              │
│   │                   AWS Services                        │              │
│   │  ┌──────────┐  ┌─────┐  ┌──────┐  ┌────┐  ┌───────┐ │              │
│   │  │ DynamoDB  │  │ S3  │  │Lambda│  │ACM │  │Route53│ │              │
│   │  │ (5 tables)│  │     │  │      │  │    │  │       │ │              │
│   │  └──────────┘  └─────┘  └──────┘  └────┘  └───────┘ │              │
│   └──────────────────────────────────────────────────────┘              │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. File / Directory Structure

```
mini-vercel/
├── pom.xml                         # Maven config: Spring Boot 4.1, AWS SDK 2.49, jjwt 0.13
├── mvnw / mvnw.cmd                 # Maven wrapper
├── build.sh                        # Build script
├── src/
│   ├── main/
│   │   ├── java/com/minivercel/
│   │   │   ├── MiniVercelApplication.java       # @SpringBootApplication + @EnableAsync
│   │   │   │
│   │   │   ├── auth/                            # ── Authentication ──
│   │   │   │   ├── AuthController.java          # POST /api/auth/register, /api/auth/login
│   │   │   │   ├── JwtService.java              # HS256 sign & verify (jjwt)
│   │   │   │   ├── JwtAuthFilter.java           # OncePerRequestFilter → extracts Bearer token
│   │   │   │   └── dto/
│   │   │   │       ├── AuthResponse.java        # {token, tokenType, expiresInSeconds, userId, email}
│   │   │   │       ├── LoginRequest.java        # {email, password}
│   │   │   │       └── RegisterRequest.java     # {email, password}
│   │   │   │
│   │   │   ├── user/                            # ── User Management ──
│   │   │   │   ├── User.java                    # DynamoDB entity: userId(PK), email(GSI), passwordHash
│   │   │   │   ├── UserRepository.java          # DynamoDB ops + email uniqueness via email-claims table
│   │   │   │   └── UserService.java             # register() + login() with BCrypt
│   │   │   │
│   │   │   ├── project/                         # ── Project CRUD ──
│   │   │   │   ├── Project.java                 # DynamoDB entity: projectId(PK), userId(GSI), gitRepoUrl, buildCommand, outputDir, productionBranch, functionDir/Runtime/Handler
│   │   │   │   ├── ProjectRepository.java       # findById, findByUserId (GSI query), findByGitRepoUrl (scan), findAll
│   │   │   │   ├── ProjectService.java          # CRUD + ownership check (requireOwned) + quota check
│   │   │   │   ├── ProjectController.java       # GET/POST/PUT/DELETE  /api/projects/**
│   │   │   │   └── dto/
│   │   │   │       ├── CreateProjectRequest.java
│   │   │   │       ├── UpdateProjectRequest.java
│   │   │   │       └── ProjectResponse.java
│   │   │   │
│   │   │   ├── deployment/                      # ── Deployment Pipeline ──
│   │   │   │   ├── Deployment.java              # DynamoDB entity: deploymentId(PK), projectId(GSI+sort:createdAt), status, deployUrl, functionUrl, branch, environment, commitSha, logs, errorMessage
│   │   │   │   ├── DeploymentStatus.java        # Enum: QUEUED → BUILDING → SUCCESS/FAILED/CANCELLED/REMOVED
│   │   │   │   ├── DeploymentRepository.java    # findById, findByProjectId (GSI, newest-first)
│   │   │   │   ├── DeploymentService.java       # THE CORE: enqueue → triggerAsync → clone → build → publish → deploy function → record
│   │   │   │   ├── DeploymentController.java    # POST trigger, GET list, GET get, POST cancel, POST rollback, DELETE previews
│   │   │   │   └── dto/
│   │   │   │       ├── DeploymentResponse.java
│   │   │   │       └── CancelDeploymentRequest.java
│   │   │   │
│   │   │   ├── build/                           # ── Build System ──
│   │   │   │   ├── BuildRunner.java             # Interface: run(BuildRequest, workDir) → BuildResult
│   │   │   │   ├── LocalBuildRunner.java         # Implementation: git clone → npm run build → validate artifact
│   │   │   │   ├── BuildRequest.java            # Record: repoUrl, buildCommand, outputDir, branch, commitSha
│   │   │   │   ├── BuildResult.java             # Record: success, artifactDir, repoDir, logs, errorMessage
│   │   │   │   └── S3Publisher.java             # publish (upload files), deletePrefix (teardown), copyPrefix (rollback)
│   │   │   │
│   │   │   ├── function/                        # ── Serverless Functions ──
│   │   │   │   ├── FunctionDeployer.java        # Interface: deploy(repoDir, functionName, logSink) + remove(name)
│   │   │   │   ├── LambdaFunctionDeployer.java  # Implementation: detect api/|functions/ → npm install → zip → create/update Lambda → Function URL
│   │   │   │   └── FunctionDeployResult.java    # Record: deployed/skipped/failed + functionName + functionUrl
│   │   │   │
│   │   │   ├── domain/                          # ── Custom Domains ──
│   │   │   │   ├── Domain.java                  # DynamoDB entity: domainId(PK), projectId(GSI), domainName, certificateArn, validationRecord*, cloudfrontTarget, status
│   │   │   │   ├── DomainStatus.java            # Enum: PENDING_VALIDATION, ISSUED, FAILED, REMOVED
│   │   │   │   ├── DomainRepository.java
│   │   │   │   ├── DomainService.java           # addDomain → request ACM cert → auto-publish DNS validation → track
│   │   │   │   ├── DomainController.java        # POST add, GET list, GET get, POST refresh, DELETE remove
│   │   │   │   ├── CertificateProvisioner.java  # Interface
│   │   │   │   ├── AcmCertificateProvisioner.java # AWS ACM implementation
│   │   │   │   ├── CertificateInfo.java
│   │   │   │   ├── DnsManager.java              # Interface
│   │   │   │   ├── Route53DnsManager.java       # AWS Route53 implementation
│   │   │   │   └── dto/
│   │   │   │       ├── AddDomainRequest.java
│   │   │   │       └── DomainResponse.java
│   │   │   │
│   │   │   ├── webhook/                         # ── GitHub Webhooks ──
│   │   │   │   ├── GitHubWebhookController.java # POST /api/webhooks/github — signature verification, push → deploy, PR close → preview teardown, branch delete → preview teardown
│   │   │   │   └── dto/
│   │   │   │       ├── GitHubPushEvent.java     # Parses raw JSON push payload
│   │   │   │       └── GitHubPullRequestEvent.java # Parses raw JSON PR payload
│   │   │   │
│   │   │   ├── admin/                           # ── Admin Monitoring ──
│   │   │   │   └── AdminController.java         # GET /api/admin/stats, GET /api/admin/active-deployments
│   │   │   │
│   │   │   ├── quota/                           # ── Quota Enforcement ──
│   │   │   │   └── QuotaService.java            # checkProjectQuota (max projects per user)
│   │   │   │
│   │   │   ├── ratelimit/                       # ── Rate Limiting ──
│   │   │   │   └── RateLimitService.java        # checkDeploymentLimits (max active + max per hour)
│   │   │   │
│   │   │   ├── config/                          # ── Configuration ──
│   │   │   │   ├── AppProperties.java           # @ConfigurationProperties: all settings in typed records
│   │   │   │   ├── SecurityConfig.java          # CSRF disabled, stateless sessions, JWT filter, public routes
│   │   │   │   ├── DynamoDbConfig.java          # Beans: DynamoDbClient, S3Client, LambdaClient, AcmClient, Route53Client
│   │   │   │   ├── TableInitializer.java        # Creates 5 DynamoDB tables + GSIs on startup if absent
│   │   │   │   └── TableNames.java              # Prefixed table name resolution (mv-users, mv-projects, etc.)
│   │   │   │
│   │   │   └── common/                          # ── Shared Utilities ──
│   │   │       ├── CurrentUser.java             # @AuthenticationPrincipal alias annotation
│   │   │       ├── ApiError.java                # Standard error envelope {error, message}
│   │   │       ├── ApiExceptionHandler.java     # @ControllerAdvice maps exceptions → HTTP status codes
│   │   │       ├── BadRequestException.java     # → 400
│   │   │       ├── NotFoundException.java       # → 404
│   │   │       └── RateLimitException.java      # → 429
│   │   │
│   │   └── resources/
│   │       └── application.yml                  # All configuration (JWT, AWS, build, webhook, limits)
│   │
│   └── test/                                    # Unit + integration tests
│
├── cli/                                         # ── Node.js CLI ──
│   ├── package.json                             # hosty-cli v1.0.0, Node 20+
│   ├── bin/
│   │   ├── hosty.js                             # CLI entry point: arg parsing, command routing
│   │   └── mini-vercel.js                       # Alias entry
│   ├── src/
│   │   ├── commands.js                          # All CLI command implementations
│   │   ├── api.js                               # HTTP client (native fetch, no deps)
│   │   └── config.js                            # Token/config persistence (~/.hosty-config.json)
│   └── test/
│
├── scripts/                                     # ── AWS Setup Scripts ──
│   ├── setup-aws.ps1                            # PowerShell: creates IAM roles, S3 bucket, DynamoDB tables
│   ├── setup-aws.sh                             # Bash equivalent
│   └── test-aws-connection.ps1                  # Verifies AWS credentials + connectivity
│
└── *.md                                         # Documentation files
```

---

## 4. DynamoDB Data Model

### Tables (all PAY_PER_REQUEST, prefix `mv-`)

| Table | Partition Key | Sort Key | GSIs |
|---|---|---|---|
| `mv-users` | `userId` (S) | — | `email-index` (PK: `email`) |
| `mv-projects` | `projectId` (S) | — | `userId-index` (PK: `userId`) |
| `mv-deployments` | `deploymentId` (S) | — | `projectId-createdAt-index` (PK: `projectId`, SK: `createdAt`) |
| `mv-email-claims` | `email` (S) | — | — |
| `mv-domains` | `domainId` (S) | — | `projectId-index` (PK: `projectId`) |

### Entity Fields

#### User
```
userId        (String, PK)   — UUID
email         (String, GSI)  — lowercase, unique (enforced by email-claims table)
passwordHash  (String)       — BCrypt hash
createdAt     (String)       — ISO-8601 instant
```

#### Project
```
projectId       (String, PK)   — UUID
userId          (String, GSI)  — owner
name            (String)       — display name
gitRepoUrl      (String)       — HTTPS Git URL
buildCommand    (String)       — e.g. "npm run build"
outputDir       (String)       — e.g. "dist", "build", ".next"
productionBranch(String)       — e.g. "main" (null = "main")
functionDir     (String)       — e.g. "api" or "functions" (Phase 6)
functionRuntime (String)       — e.g. "nodejs20.x"
functionHandler (String)       — e.g. "index.handler"
createdAt       (String)       — ISO-8601 instant
```

#### Deployment
```
deploymentId      (String, PK)                — UUID
projectId         (String, GSI partition)     — owning project
createdAt         (String, GSI sort)          — ISO-8601 (newest-first queries)
userId            (String)                     — denormalized for ownership checks
status            (String)                     — queued | building | success | failed | cancelled | removed
deployUrl         (String)                     — S3 website URL or file:// path
functionUrl       (String)                     — Lambda Function URL (null if static-only)
lambdaFunctionName(String)                     — AWS Lambda function name
branch            (String)                     — git branch (null for manual triggers)
environment       (String)                     — "production" or "preview"
commitSha         (String)                     — git commit SHA
logs              (String)                     — build output (capped at 32 KB)
errorMessage      (String)                     — failure reason
finishedAt        (String)                     — ISO-8601 instant
```

#### Domain
```
domainId             (String, PK)            — UUID
projectId            (String, GSI)           — attached project
userId               (String)                — denormalized for ownership
domainName           (String)                — e.g. "app.example.com"
status               (String)                — pending_validation | issued | failed | removed
certificateArn       (String)                — ACM certificate ARN
validationRecordName (String)                — DNS CNAME name for ACM validation
validationRecordValue(String)                — DNS CNAME value
validationRecordType (String)                — "CNAME"
cloudfrontTarget     (String)                — CloudFront domain to CNAME at
createdAt            (String)                — ISO-8601 instant
validatedAt          (String)                — when cert was issued
errorMessage         (String)
```

---

## 5. API Endpoints — Complete Reference

### Authentication (public)
| Method | Path | Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | `{email, password}` | `{token, tokenType, expiresInSeconds, userId, email}` | Creates account + returns JWT |
| `POST` | `/api/auth/login` | `{email, password}` | Same as above | Returns JWT |

### Projects (authenticated — `Authorization: Bearer <token>`)
| Method | Path | Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/projects` | — | `[ProjectResponse]` | List caller's projects |
| `POST` | `/api/projects` | `{name, gitRepoUrl, buildCommand, outputDir, productionBranch?}` | `ProjectResponse` | Create project (checks quota) |
| `GET` | `/api/projects/{id}` | — | `ProjectResponse` | Get one project |
| `PUT` | `/api/projects/{id}` | `{name, buildCommand, outputDir, productionBranch?}` | `ProjectResponse` | Update project |
| `DELETE` | `/api/projects/{id}` | — | `204` | Delete project |

### Deployments (authenticated)
| Method | Path | Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/projects/{pid}/deployments` | `{branch?, commitSha?}` (optional) | `DeploymentResponse` | Trigger deploy (async build) |
| `GET` | `/api/projects/{pid}/deployments` | — | `[DeploymentResponse]` (max 20) | List deployments, newest first |
| `GET` | `/api/projects/{pid}/deployments/{did}` | — | `DeploymentResponse` | Get single deployment (poll for status) |
| `POST` | `/api/projects/{pid}/deployments/{did}/cancel` | `{reason}` | `DeploymentResponse` | Cancel queued/building deploy |
| `POST` | `/api/projects/{pid}/deployments/{did}/rollback` | — | `DeploymentResponse` | Instant rollback (S3 copy, no rebuild) |
| `DELETE` | `/api/projects/{pid}/deployments/previews?branch=X` | — | `{branch, removed}` | Tear down preview: delete Lambda + S3 + mark removed |

### Custom Domains (authenticated)
| Method | Path | Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/projects/{pid}/domains` | `{domainName}` | `DomainResponse` | Attach domain → request ACM cert |
| `GET` | `/api/projects/{pid}/domains` | — | `[DomainResponse]` | List project's domains |
| `GET` | `/api/projects/{pid}/domains/{did}` | — | `DomainResponse` | Get one domain |
| `POST` | `/api/projects/{pid}/domains/{did}/refresh` | — | `DomainResponse` | Re-check ACM cert status |
| `DELETE` | `/api/projects/{pid}/domains/{did}` | — | `204` | Remove domain + delete cert |

### GitHub Webhooks (public — signature-verified)
| Method | Path | Headers | Description |
|---|---|---|---|
| `POST` | `/api/webhooks/github` | `X-Hub-Signature-256`, `X-GitHub-Event` | Handles `push` (deploy or preview teardown on branch delete) and `pull_request` (preview teardown on close) |

### Admin (currently public — TODO: add auth)
| Method | Path | Response | Description |
|---|---|---|---|
| `GET` | `/api/admin/stats` | `{totalUsers, totalProjects, totalDeployments, deploymentsLastHour, activeDeployments}` | System overview |
| `GET` | `/api/admin/active-deployments` | `[{deploymentId, projectId, projectName, status, createdAt}]` | Currently building/queued |

---

## 6. Deployment Pipeline — Step by Step

This is the core flow (in `DeploymentService`):

```
User calls POST /api/projects/{pid}/deployments
          │
          ▼
    ┌─ enqueue() ─────────────────────────────────────────────────┐
    │  1. Verify project ownership (projectService.requireOwned)  │
    │  2. Check rate limits (rateLimitService.checkDeploymentLimits)│
    │  3. Classify environment: production vs preview              │
    │     - branch == productionBranch (or null) → "production"    │
    │     - anything else → "preview"                              │
    │  4. Create Deployment record (status=QUEUED)                 │
    │  5. Call triggerAsync() → returns immediately                 │
    │  6. Return DeploymentResponse to client                      │
    └──────────────────────────────────────────────────────────────┘
          │
          ▼  (async, on Spring's @Async executor)
    ┌─ triggerAsync() ────────────────────────────────────────────────────┐
    │  1. Set status → BUILDING                                           │
    │  2. Create temp working directory                                   │
    │                                                                     │
    │  3. ── BUILD (LocalBuildRunner.run) ──                               │
    │     a. Validate: git host in allowlist, build command in allowlist   │
    │     b. git clone --depth 1 [--branch X] <repoUrl> repo/            │
    │     c. (optional) git checkout <commitSha>                          │
    │     d. Run build command (npm run build / yarn build / etc.)         │
    │        - On Windows: wrapped with "cmd /c" for .cmd shims           │
    │     e. Verify output directory exists                               │
    │     f. Check artifact size ≤ maxArtifactBytes (100 MB)              │
    │     → Returns BuildResult{success, artifactDir, repoDir, logs}      │
    │                                                                     │
    │  4. ── PUBLISH ──                                                   │
    │     If S3 bucket configured:                                        │
    │       S3Publisher.publish(artifactDir, projectId, deploymentId)      │
    │       → uploads all files to s3://bucket/projectId/deploymentId/    │
    │       → sets correct Content-Type for html/css/js/json/images       │
    │       → deployUrl = publicBaseUrl/projectId/deploymentId/index.html │
    │     Else (local mode):                                              │
    │       Copy artifacts to ./artifacts/projectId/deploymentId/         │
    │       → deployUrl = file:///path/to/artifacts                       │
    │                                                                     │
    │  5. ── SERVERLESS FUNCTION (LambdaFunctionDeployer.deploy) ──       │
    │     a. Check if Lambda role ARN is configured (skip if not)         │
    │     b. Look for api/ or functions/ directory in repo                │
    │     c. npm install --omit=dev (if package.json present)             │
    │     d. Zip the function directory in memory                         │
    │     e. Create or update Lambda function                             │
    │        - Production: function name = "mv-<projectId>"               │
    │        - Preview: function name = "mv-<projectId>-<branchSlug>"     │
    │     f. Ensure Function URL exists (NONE auth = public)              │
    │     g. Grant lambda:InvokeFunctionUrl permission (public invoke)    │
    │     → Returns FunctionDeployResult{deployed/skipped/failed}         │
    │                                                                     │
    │  6. Set status → SUCCESS (or FAILED)                                │
    │  7. Save deployment record with deployUrl, functionUrl, logs        │
    │  8. Clean up temp directory                                         │
    └─────────────────────────────────────────────────────────────────────┘
```

### Rollback Flow (zero-rebuild)
```
POST /api/projects/{pid}/deployments/{targetId}/rollback
  1. Verify ownership of project AND target deployment
  2. Target must have status = SUCCESS
  3. S3Publisher.copyPrefix(projectId/targetId → projectId/newId)
  4. Create new Deployment record (status=SUCCESS, instant)
  5. Reuse target's Lambda function name + Function URL
  6. Return immediately (milliseconds, no git clone or build)
```

### Preview Teardown Flow
```
DELETE /api/projects/{pid}/deployments/previews?branch=X
  OR automatically on GitHub branch delete / PR close webhook
  1. Delete branch-scoped Lambda function (mv-<projectId>-<branchSlug>)
  2. Delete S3 artifacts for each preview deployment of that branch
  3. Mark deployment records as status=REMOVED
```

---

## 7. Authentication & Security

### JWT Flow
1. User calls `POST /api/auth/register` or `/api/auth/login`
2. `UserService` validates credentials (BCrypt), calls `JwtService.issue(userId, email)`
3. JWT payload: `{sub: userId, email: email, iat, exp}` — signed with HS256
4. Client sends `Authorization: Bearer <token>` on every subsequent request
5. `JwtAuthFilter` (OncePerRequestFilter) extracts, verifies, and puts `userId` as the Spring Security principal
6. Controllers get the userId via `@CurrentUser String userId` (alias for `@AuthenticationPrincipal`)

### Security Config
- **CSRF disabled** (stateless API, no cookies)
- **Stateless sessions** (no server-side session)
- **Public routes**: `/api/auth/**`, `/api/webhooks/**`, `/api/admin/**`, `/sites/**`, `/actuator/health`
- **Everything else**: requires valid JWT
- **Ownership model**: projects/deployments/domains always verify `userId` matches — other users' resources return 404 (not 403) to avoid confirming existence

### Email Uniqueness
DynamoDB GSIs don't enforce uniqueness. The system uses a separate `mv-email-claims` table with a **conditional put** (`attribute_not_exists(email)`) to guarantee one account per email address.

### Webhook Signature Verification
GitHub webhooks are verified using **HMAC-SHA256** (`X-Hub-Signature-256` header). The secret is in `minivercel.webhook.github-secret`. If the secret is blank (dev mode), verification is skipped.

---

## 8. All Services & Their Functions

### `UserService`
| Method | What it does |
|---|---|
| `register(RegisterRequest)` | Normalizes email, BCrypt hashes password, saves (with email uniqueness check), returns JWT |
| `login(LoginRequest)` | Finds by email, verifies BCrypt, returns JWT |

### `JwtService`
| Method | What it does |
|---|---|
| `issue(userId, email)` | Creates HS256 JWT with 24h expiry |
| `verify(token)` | Returns `Optional<userId>` — empty if invalid/expired |
| `expirySeconds()` | Returns configured expiry duration |

### `ProjectService`
| Method | What it does |
|---|---|
| `listForUser(userId)` | Queries GSI, returns all projects owned by user |
| `get(projectId, userId)` | Returns project if owned, 404 otherwise |
| `create(request, userId)` | Checks quota, creates with UUID, saves to DynamoDB |
| `update(projectId, request, userId)` | Ownership check, updates fields |
| `delete(projectId, userId)` | Ownership check, deletes from DynamoDB |
| `requireOwned(projectId, userId)` | Loads project, asserts ownership — 404 if not found or wrong user |

### `DeploymentService`
| Method | What it does |
|---|---|
| `enqueue(projectId, userId)` | Creates QUEUED record, kicks off async build |
| `enqueue(projectId, userId, commitSha)` | Same, with commit SHA |
| `enqueue(projectId, userId, branch, commitSha)` | Full version: classifies environment, rate-limits, async build |
| `triggerAsync(deployment, project)` | **@Async** — clone → build → publish → deploy function → record result |
| `get(deploymentId, userId)` | Ownership check, returns deployment |
| `listForProject(projectId, userId)` | Ownership check, returns up to 20 deployments (newest first) |
| `cancel(deploymentId, userId, reason)` | Sets status=CANCELLED if still queued/building |
| `rollback(projectId, targetId, userId)` | Instant rollback: copies S3 artifacts, creates new SUCCESS record |
| `removePreview(projectId, userId, branch)` | Tears down preview environment (with ownership check) |
| `removePreviewForWebhook(projectId, branch)` | Same, without ownership check (for webhook path) |

### `LocalBuildRunner`
| Method | What it does |
|---|---|
| `run(BuildRequest, workDir)` | Validates host + command → git clone → build → validate artifact size → returns BuildResult |

### `S3Publisher`
| Method | What it does |
|---|---|
| `publish(artifactDir, projectId, deploymentId)` | Walks directory, uploads each file to S3 with correct content-type |
| `deletePrefix(prefix)` | Batch-deletes all S3 objects under a prefix |
| `copyPrefix(source, destination)` | Copies all objects from one prefix to another (for rollback) |

### `LambdaFunctionDeployer`
| Method | What it does |
|---|---|
| `deploy(repoDir, functionName, logSink)` | Detects function dir → npm install → zip → create/update Lambda → ensure Function URL + public invoke permission |
| `remove(functionName)` | Deletes Lambda function (no-op if not found) |

### `DomainService`
| Method | What it does |
|---|---|
| `addDomain(projectId, userId, domainName)` | Ownership check, request ACM cert, auto-publish DNS validation if Route53 configured, save record |
| `listDomains(projectId, userId)` | List domains for project |
| `getDomain(domainId, userId)` | Get domain with ownership check |
| `refreshStatus(domainId, userId)` | Re-read cert from ACM, update status (pending → issued) |
| `removeDomain(domainId, userId)` | Delete ACM cert, mark domain REMOVED |

### `QuotaService`
| Method | What it does |
|---|---|
| `checkProjectQuota(userId)` | Throws 429 if user has ≥ `maxProjectsPerUser` projects |

### `RateLimitService`
| Method | What it does |
|---|---|
| `checkDeploymentLimits(projectId)` | Checks active deployment limit AND hourly deployment limit; throws 429 |

### `GitHubWebhookController`
| Method | What it does |
|---|---|
| `handleGitHubPush(...)` | Verifies HMAC signature → dispatches push (deploy) or branch-delete (preview teardown) |
| `handlePullRequest(...)` | On PR close → tears down preview for head branch |

### `AdminController`
| Method | What it does |
|---|---|
| `stats()` | Returns total users, projects, deployments, active, last-hour |
| `activeDeployments()` | Lists all currently queued/building deployments |

---

## 9. Configuration Reference (`application.yml`)

```yaml
minivercel:
  jwt:
    secret: <32+ byte HS256 key>     # env: JWT_SECRET
    expiry: 24h

  aws:
    region: ap-south-1
    table-prefix: mv-
    dynamodb-endpoint: <blank for real AWS, http://localhost:8000 for DynamoDB Local>
    artifact-bucket: mini-vercel-artifacts-364660314424    # env: ARTIFACT_BUCKET
    public-base-url: <S3 website or CloudFront URL>        # env: PUBLIC_BASE_URL
    lambda:
      role-arn: <IAM role ARN>        # env: LAMBDA_ROLE_ARN (blank = skip functions)
      runtime: nodejs20.x
      handler: index.handler
      timeout-seconds: 30
      memory-mb: 256
      url-auth-type: NONE             # NONE = public, AWS_IAM = signed
    domains:
      cert-region: us-east-1          # MUST be us-east-1 for CloudFront
      cloudfront-target: <dxxx.cloudfront.net>
      route53-hosted-zone-id: <zone-id>  # if set, auto-publishes DNS validation

  build:
    local-artifact-dir: ./artifacts   # used when artifact-bucket is blank
    timeout: 10m
    max-artifact-bytes: 104857600     # 100 MB
    allowed-git-hosts: [github.com, gitlab.com]
    allowed-build-commands: [npm run build, npm ci && npm run build, yarn build, pnpm build]

  webhook:
    github-secret: <HMAC secret>      # env: GITHUB_WEBHOOK_SECRET

  limits:
    max-projects-per-user: 10
    max-deployments-per-hour: 10
    max-active-deployments: 3
```

---

## 10. CLI (`hosty`) — Complete Reference

The CLI is a zero-dependency Node.js 20+ tool in `cli/`. Config is stored in `~/.hosty-config.json`.

### Commands
```
hosty login                        # Interactive email/password → stores JWT
hosty logout                       # Clears stored credentials
hosty whoami                       # Shows current user, project count
hosty projects list                # List all projects
hosty projects create              # Interactive project creation
hosty projects delete <projectId>  # Delete a project
hosty deploy [projectId]           # Trigger deploy + poll status live
hosty deployments list <projectId> # List past deployments
hosty rollback <projectId> <deploymentId>  # Instant zero-rebuild rollback
```

### CLI Flags
```
--api-url <url>    Override API URL (default: http://localhost:8080)
--email <email>    Email for login
--password <pwd>   Password for login
--branch <branch>  Git branch to deploy
--commit <sha>     Commit SHA to record
--name <name>      Project name (for create)
--repo <url>       Git HTTPS URL (for create)
```

### CLI Architecture
- `bin/hosty.js` — Entry point, arg parsing, command routing
- `src/commands.js` — All command implementations (loginCommand, deployCommand, etc.)
- `src/api.js` — HTTP client wrapping native `fetch`, exported as `api` object
- `src/config.js` — Read/write `~/.hosty-config.json` (token, apiUrl, userId, email)

---

## 11. Environment Classification (Phase 7)

```
Push to "main" (or project's productionBranch) → environment = "production"
Push to any other branch                       → environment = "preview"
Manual deploy with no branch specified         → environment = "production"
```

- **Production** Lambda name: `mv-<projectId>`
- **Preview** Lambda name: `mv-<projectId>-<branchSlug>` (branch slugified: lowercase, `[^a-z0-9-]` → `-`, max 40 chars)
- Previews are auto-cleaned on:
  - Branch deletion (GitHub push webhook with `deleted: true`)
  - PR close/merge (GitHub `pull_request` webhook with `action: closed`)
  - Manual `DELETE /api/projects/{pid}/deployments/previews?branch=X`

---

## 12. Error Handling

| Exception | HTTP Status | Scenario |
|---|---|---|
| `BadRequestException` | 400 | Duplicate email, invalid input |
| `BadCredentialsException` | 401 | Wrong password, no such user |
| `NotFoundException` | 404 | Project/deployment/domain not found OR not owned |
| `RateLimitException` | 429 | Quota exceeded (projects, deployments) |
| `IllegalStateException` | 400 | Cancel already-finished deployment |

All errors return: `{"error": "<code>", "message": "<human-readable>"}`

---

## 13. Key Design Decisions

1. **DynamoDB over RDS**: Serverless, pay-per-request, no connection pools, auto-scales to zero cost when idle
2. **Email uniqueness via claims table**: GSIs can't enforce uniqueness in DynamoDB — conditional put on a separate table solves this
3. **Ownership = 404 not 403**: Never confirms which IDs exist to other users
4. **@Async deployment**: HTTP response returns immediately with `status: queued`; client polls for completion
5. **BuildRunner interface**: Currently `LocalBuildRunner` (runs on same machine); designed so a `CodeBuildRunner` can slot in without changing `DeploymentService`
6. **FunctionDeployer interface**: Currently `LambdaFunctionDeployer`; designed for container/Fargate swap later
7. **CertificateProvisioner/DnsManager interfaces**: Currently ACM+Route53; abstracted for other DNS providers
8. **Branch-scoped Lambda names**: Preview functions never overwrite production; each branch gets its own Lambda
9. **Rollback via S3 copy**: No git clone or rebuild — copies artifacts and reuses the Lambda function in milliseconds
10. **Logs capped at 32 KB**: DynamoDB item size limit is 400 KB; build logs are truncated to stay safe

---

## 14. How to Run Locally

### Prerequisites
- Java 21+
- Node.js 20+ (for CLI)
- Docker (for DynamoDB Local)

### Steps
```bash
# 1. Start DynamoDB Local
docker run -p 8000:8000 amazon/dynamodb-local

# 2. Set environment
export DYNAMODB_ENDPOINT=http://localhost:8000
export ARTIFACT_BUCKET=           # blank = local mode
export LAMBDA_ROLE_ARN=           # blank = skip functions

# 3. Run the server
./mvnw spring-boot:run

# 4. Use the CLI
cd cli
node bin/hosty.js login --email test@example.com --password test123
node bin/hosty.js deploy
```

### Local Mode Behavior
- DynamoDB Local for database (dummy credentials)
- Artifacts saved to `./artifacts/` instead of S3
- Lambda deployment skipped (no role ARN)
- ACM/Route53 beans created with dummy credentials (never called)
- Deploy URL is `file:///path/to/artifacts/...`

---

## 15. AWS Services Used (Production)

| Service | Purpose | Region |
|---|---|---|
| **DynamoDB** | 5 tables (users, projects, deployments, email-claims, domains) | ap-south-1 |
| **S3** | Static site artifact storage + website hosting | ap-south-1 |
| **Lambda** | Serverless function execution | ap-south-1 |
| **Lambda Function URLs** | Public HTTP endpoints for functions | ap-south-1 |
| **ACM** | SSL certificates for custom domains | us-east-1 (required for CloudFront) |
| **Route53** | DNS validation record auto-publish | Global |
| **IAM** | Lambda execution role (`mini-vercel-lambda-exec`) | Global |

---

## 16. Rate Limits & Quotas

| Limit | Default | Config Key |
|---|---|---|
| Max projects per user | 10 | `minivercel.limits.max-projects-per-user` |
| Max deployments per project per hour | 10 | `minivercel.limits.max-deployments-per-hour` |
| Max active (queued/building) deployments per project | 3 | `minivercel.limits.max-active-deployments` |
| Max artifact size | 100 MB | `minivercel.build.max-artifact-bytes` |
| Build timeout | 10 min | `minivercel.build.timeout` |
| Allowed git hosts | github.com, gitlab.com | `minivercel.build.allowed-git-hosts` |
| Allowed build commands | npm run build, npm ci && npm run build, yarn build, pnpm build | `minivercel.build.allowed-build-commands` |
