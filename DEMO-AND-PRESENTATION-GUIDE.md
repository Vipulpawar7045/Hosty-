# 🎓 Hosty — Complete Project Demo & Presentation Guide

> **Project Name**: Hosty 🚀 (Formerly Mini-Vercel)  
> **Tagline**: Self-Serve Cloud Deployment Platform with Zero-Rebuild Rollbacks and AWS Lambda Serverless Functions  
> **Tech Stack**: Spring Boot 4 (Java 21), AWS SDK v2, Amazon S3, AWS Lambda, Amazon DynamoDB, Node.js CLI  
> **Cloud Region**: AWS `ap-south-1` (100% Free-Tier / $0 Spend Architecture)

---

## 📑 Table of Contents
1. [Project Overview & Key Highlights](#-1-project-overview--key-highlights)
2. [System Architecture](#-2-system-architecture)
3. [Pre-Demo Checklist (5 Minutes Before)](#-3-pre-demo-checklist-5-minutes-before)
4. [Step-by-Step Live Demo Runbook (Teacher Presentation Script)](#-4-step-by-step-live-demo-runbook-teacher-presentation-script)
5. [Teacher Presentation Points & Viva Q&A](#-5-teacher-presentation-points--viva-qa)
6. [Quick Command Reference Cheat-Sheet](#-6-quick-command-reference-cheat-sheet)

---

## 🌟 1. Project Overview & Key Highlights

### What is Hosty?
Hosty is a self-serve cloud hosting platform modeled after Vercel. A developer can connect a Git repository, push code or use the CLI, and Hosty automatically:
1. Clones the code into an isolated build sandbox.
2. Auto-detects the frontend framework (React, Vite, Vue, Next.js, static HTML) and executes `npm ci && npm run build`.
3. Publishes static build artifacts directly to an **Amazon S3 static website**.
4. Auto-detects serverless API routes (`api/` or `functions/`) and deploys them to **AWS Lambda** with public HTTPS Function URLs.
5. Manages preview environments with automatic teardown on Pull Request merge.
6. Performs **sub-second instant rollbacks** without rebuilding code.

### What makes Hosty unique for this college / academic project?
- **Real Cloud Native Integration**: Not just local mocks — it deploys to live Amazon S3, AWS Lambda, and DynamoDB.
- **$0 Free-Tier Architecture**: Built exclusively on AWS Always-Free services (`PAY_PER_REQUEST` DynamoDB, Lambda 1M invocations, S3 web hosting) with zero EC2/NAT Gateway overhead.
- **Instant Zero-Rebuild Rollbacks**: Traditional CI/CD re-runs git clone and build (taking 2–5 minutes). Hosty clones S3 asset prefixes in milliseconds.
- **Developer CLI Tool**: Includes a full-featured terminal CLI (`hosty`) built with Node.js.
- **75 Automated Unit & Integration Tests**: 62 JUnit 5 backend tests and 13 CLI tests with 100% pass rate.

---

## 🏛️ 2. System Architecture

```
                      ┌────────────────────────────┐
                      │    Developer / User        │
                      └──────────────┬─────────────┘
                                     │
                 ┌───────────────────┼───────────────────┐
                 │ git push          │ hosty CLI         │ HTTP REST
                 ▼                   ▼                   ▼
    ┌──────────────────────┐ ┌───────────────┐ ┌───────────────────┐
    │ GitHub Push / PR     │ │ Hosty CLI     │ │ Web Dashboard     │
    │ Webhook (HMAC-SHA256)│ │ (Node.js 22)  │ │ (REST API)        │
    └──────────┬───────────┘ └───────┬───────┘ └─────────┬─────────┘
               │                     │                   │
               └─────────────────────┼───────────────────┘
                                     ▼
                  ┌─────────────────────────────────────┐
                  │       Hosty Backend Engine          │
                  │     (Spring Boot 4 / Java 21)       │
                  └──────────────────┬──────────────────┘
                                     │
      ┌──────────────────────────────┼──────────────────────────────┐
      ▼                              ▼                              ▼
┌──────────────┐              ┌──────────────┐              ┌──────────────┐
│  DynamoDB    │              │   Amazon S3  │              │  AWS Lambda  │
│  (Metadata)  │              │(Static Sites)│              │ (Serverless) │
│ • Users      │              │ • Public Web │              │ • Node.js    │
│ • Projects   │              │   Hosting    │              │   Routes     │
│ • Deployments│              │ • Fast Asset │              │ • Public     │
│ • Domains    │              │   Cloning    │              │   HTTPS URLs │
└──────────────┘              └──────────────┘              └──────────────┘
```

---

## ✅ 3. Pre-Demo Checklist (5 Minutes Before)

Before your teacher sits down, do these 3 quick checks:

1. **Verify Backend Server is running**:
   Open a terminal and test:
   ```powershell
   curl.exe -s http://localhost:8080/actuator/health
   ```
   *(If it responds with JSON, the server is live!)*

   *If it is not running, start it:*
   ```powershell
   cd C:\Users\shuhamjadhav658\OneDrive\Desktop\cloud\mini-vercel
   java -jar target\hosty-0.0.1-SNAPSHOT.jar
   ```

2. **Verify AWS Internet Connection**:
   Ensure your laptop is connected to the internet so AWS S3 and AWS Lambda can be reached.

3. **Open 2 Terminal Windows**:
   - **Terminal 1**: Running the Spring Boot server (`java -jar target\hosty-0.0.1-SNAPSHOT.jar`)
   - **Terminal 2**: Ready for running the `hosty` CLI commands!

---

## 🎬 4. Step-by-Step Live Demo Runbook (Teacher Presentation Script)

Follow these exact steps during your demonstration:

---

### Step 1: Introduce the Project (30 seconds)
> *"Hello sir/ma'am, today I am presenting **Hosty**, a serverless cloud hosting platform inspired by Vercel. I built this using Spring Boot 4 with Java 21 on the backend, AWS SDK v2, Amazon S3, AWS Lambda, Amazon DynamoDB, and a custom developer CLI in Node.js. It allows developers to deploy full-stack static sites and serverless APIs with automatic builds and instant rollbacks."*

---

### Step 2: Show Test Coverage & Code Quality (30 seconds)
> *"Before demonstrating the live platform, I'd like to show the automated test suite. We followed strict test-driven engineering with 75 automated tests."*

In **Terminal 2**, run:
```powershell
cd C:\Users\shuhamjadhav658\OneDrive\Desktop\cloud\mini-vercel
.\mvnw.cmd test
```
**Point out to teacher:**
- `Tests run: 62, Failures: 0, Errors: 0` (`BUILD SUCCESS`)
- Covers security (JWT), quotas, rate-limiting, sandboxed builds, Lambda packaging, and instant rollbacks.

Next, show the CLI tests:
```powershell
cd cli
npm.cmd test
cd ..
```
**Point out to teacher:**
- `13 tests run, 13 passed` (including real end-to-end integration tests).

---

### Step 3: Demonstrate Developer Authentication & Projects via CLI (1 minute)
> *"Now, let's look at the developer experience using our custom Hosty CLI."*

In **Terminal 2**, run:
```powershell
node cli/bin/hosty.js whoami
```
**What shows on screen:**
```text
Hosty User Information
----------------------------------------
  Email:      admin@example.com
  User ID:    8eebb929-e522-4f0a-96e9-b7caa01f9508
  API Server: http://localhost:8080
  Projects:   1
```

Now list active projects:
```powershell
node cli/bin/hosty.js projects list
```
**What shows on screen:**
- Project Name: `test-site`
- Production Branch: `master`
- Connected Git Repo: `https://github.com/Vipulpawar7045/mini-vercel-test-site`

---

### Step 4: Trigger a Live Cloud Deployment (1.5 minutes)
> *"Now I will trigger a live build and deployment to AWS using the CLI."*

Run:
```powershell
node cli/bin/hosty.js deploy 6d55b862-07e2-4c2d-b7fe-8669142e15e1
```
**Explain what is happening live on screen:**
1. The CLI queues the deployment.
2. The Spring Boot backend clones the repository from GitHub into a temporary sandbox.
3. It detects the Vite / React configuration and runs `npm ci && npm run build`.
4. The built files are published directly to **Amazon S3** with public website hosting.
5. The backend detects the `api/` folder, bundles the Node.js serverless route, and deploys it to **AWS Lambda**.
6. The CLI displays the live URLs!

**Now show the live links in browser:**
- Click or open the **Static Deploy URL** in Chrome:
  `http://mini-vercel-artifacts-364660314424.s3-website.ap-south-1.amazonaws.com/.../index.html`
  *(Show the teacher the website rendered live from Amazon S3!)*

---

### Step 5: Demonstrate Serverless AWS Lambda API (1 minute)
> *"Hosty doesn't just host static files; it supports full serverless functions like Vercel. In our repo, we have an `api/index.js` file. Hosty automatically deployed it to AWS Lambda with an HTTPS Function URL."*

Run in terminal:
```powershell
curl.exe https://ddsb4mzcystbz7dtzuim73lpey0ygxky.lambda-url.ap-south-1.on.aws/
```
**Show output to teacher:**
```json
{"message":"Hello from Mini-Vercel on AWS Lambda 🚀","method":"GET","path":"/","env":"production"}
```
> *"This was executed on a real AWS Lambda container in Mumbai (ap-south-1) with zero server configuration required."*

---

### Step 6: The "Showstopper" Feature — Instant Zero-Rebuild Rollback (1.5 minutes)
> *"Now I'd like to show our key architectural innovation: **Instant Zero-Rebuild Rollbacks**."*

Explain the problem to your teacher:
> *"In standard CI/CD pipelines like Jenkins or GitHub Actions, rolling back a deployment requires checking out an old Git commit and re-running the entire 3-minute build process. In Hosty, we implemented instant S3 prefix cloning. We can roll back to any previous deployment in milliseconds without rebuilding."*

List past deployments first:
```powershell
node cli/bin/hosty.js deployments list 6d55b862-07e2-4c2d-b7fe-8669142e15e1
```

Trigger the instant rollback:
```powershell
node cli/bin/hosty.js rollback 6d55b862-07e2-4c2d-b7fe-8669142e15e1 b5f1f741-e811-4d74-836e-95fc345fe3a4
```
**What shows on screen:**
```text
✓ Instant Rollback Completed in milliseconds!
  📦 New Deployment ID:  ...
  🌐 Live Deploy URL:     http://mini-vercel-artifacts-364660314424.s3-website.ap-south-1.amazonaws.com/.../index.html
  ⚡ Serverless API URL:  https://ddsb4mzcystbz7dtzuim73lpey0ygxky.lambda-url.ap-south-1.on.aws/
```
> *"In less than 1 second, the old build assets were cloned and traffic was routed to the known-good state. Zero downtime, zero rebuild wait."*

---

## 💡 5. Teacher Presentation Points & Viva Q&A

Here are the questions your teacher or external examiner is most likely to ask, and the exact technical answers to give:

### Q1: Why did you choose Amazon S3 + Lambda instead of hosting on an EC2 instance?
**Answer**:
- **Cost**: An EC2 instance runs 24/7 and costs money even when idle. By using S3 Static Website Hosting and AWS Lambda, our platform costs **$0** when idle.
- **Scalability**: S3 can serve millions of static requests per second without configuring load balancers. Lambda scales to thousands of concurrent executions automatically.
- **Security**: There are no virtual machines or OS patches to maintain.

### Q2: How does the system prevent malicious commands during `npm run build`?
**Answer**:
- We implemented a strict **Command Whitelist** in [`LocalBuildRunner.java`](file:///C:/Users/shuhamjadhav658/OneDrive/Desktop/cloud/mini-vercel/src/main/java/com/minivercel/build/LocalBuildRunner.java).
- Only approved build scripts (`npm run build`, `npm ci`, etc.) are permitted.
- Each build executes inside an isolated temporary directory (`Files.createTempDirectory`) with a hard 10-minute timeout limit to prevent infinite loops or Denial of Service.

### Q3: How is user data stored and secured?
**Answer**:
- **Database**: Amazon DynamoDB with single-table design principles.
- **Passwords**: Hashed with **BCrypt** with salt before storage. Plaintext passwords are never saved.
- **Authentication**: Stateless **JWT (JSON Web Tokens)** signed with HMAC-SHA384. Every API request validates the token via `JwtAuthFilter`.
- **Tenant Isolation**: Every project and deployment requires matching user ownership checks (`requireOwned(projectId, userId)`).

### Q4: How do GitHub Webhooks work in this system?
**Answer**:
- GitHub sends an HTTP POST event to `/api/webhooks/github` on every push or pull request.
- Hosty verifies the authenticity of the webhook using **HMAC-SHA256 signature verification** (`X-Hub-Signature-256`) against a shared secret before processing.
- If the push is to `master`/`main`, it deploys to the **production** environment. If it's a feature branch or PR, it creates an isolated **preview** environment.

### Q5: How do preview environment teardowns work?
**Answer**:
- When a PR is closed or merged, GitHub sends a `pull_request.closed` webhook.
- Hosty's [`DeploymentServiceTeardownTest.java`](file:///C:/Users/shuhamjadhav658/OneDrive/Desktop/cloud/mini-vercel/src/test/java/com/minivercel/deployment/DeploymentServiceTeardownTest.java) cleans up the branch-scoped AWS Lambda function and deletes the S3 preview prefix, preventing orphaned resources in AWS.

---

## ⚡ 6. Quick Command Reference Cheat-Sheet

Keep these commands handy during your presentation:

| Action | Command |
| :--- | :--- |
| **Start Backend Server** | `java -jar target\hosty-0.0.1-SNAPSHOT.jar` |
| **Run All Backend Tests (62 tests)** | `.\mvnw.cmd test` |
| **Run All CLI Tests (13 tests)** | `cd cli; npm.cmd test; cd ..` |
| **Check CLI User Session** | `node cli/bin/hosty.js whoami` |
| **List Projects** | `node cli/bin/hosty.js projects list` |
| **Deploy Project** | `node cli/bin/hosty.js deploy 6d55b862-07e2-4c2d-b7fe-8669142e15e1` |
| **List Past Deployments** | `node cli/bin/hosty.js deployments list 6d55b862-07e2-4c2d-b7fe-8669142e15e1` |
| **Instant Rollback** | `node cli/bin/hosty.js rollback 6d55b862-07e2-4c2d-b7fe-8669142e15e1 <deploymentId>` |
| **Test S3 Static Site** | `curl.exe -i http://mini-vercel-artifacts-364660314424.s3-website.ap-south-1.amazonaws.com/<projectId>/<deploymentId>/index.html` |
| **Test Lambda Function URL** | `curl.exe https://ddsb4mzcystbz7dtzuim73lpey0ygxky.lambda-url.ap-south-1.on.aws/` |

---

*Good luck with your presentation! Hosty is fully functional, verified live on AWS, and backed by comprehensive test suites.*
