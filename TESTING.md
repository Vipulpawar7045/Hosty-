# Quick Test Commands

## Test Webhook Endpoint Locally

### 1. Start the Server
```bash
cd C:\Users\Admin\OneDrive\Desktop\cloud\mini-vercel
java -jar target\mini-vercel-0.0.1-SNAPSHOT.jar
```

### 2. Test Without Signature (Dev Mode)
```bash
curl -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -d '{
    "repository": {
      "clone_url": "https://github.com/Vipulpawar7045/mini-vercel-test-site.git",
      "html_url": "https://github.com/Vipulpawar7045/mini-vercel-test-site"
    },
    "head_commit": {
      "id": "abc123def456"
    },
    "after": "abc123def456"
  }'
```

Expected response:
```json
{
  "message": "Deployment triggered",
  "deploymentId": "...",
  "projectId": "..."
}
```

Or if no project found:
```json
{
  "message": "No project configured for this repository"
}
```

### 3. Check Deployment Status
```bash
curl http://localhost:8080/api/deployments/{deploymentId} \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 4. List All Deployments for Project
```bash
curl http://localhost:8080/api/projects/{projectId}/deployments \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Get Your JWT Token

### Register
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "..."
}
```

---

## Create a Project

```bash
curl -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-site",
    "gitRepoUrl": "https://github.com/Vipulpawar7045/mini-vercel-test-site.git",
    "buildCommand": "npm ci && npm run build",
    "outputDir": "dist"
  }'
```

**Important**: Make sure `gitRepoUrl` matches exactly what GitHub sends in the webhook (including or excluding `.git` suffix).

---

## Manual Deployment (Without Webhook)

```bash
curl -X POST http://localhost:8080/api/deployments \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "YOUR_PROJECT_ID"
  }'
```

---

## Setup ngrok for GitHub Webhook

```bash
# Download from https://ngrok.com/download
ngrok http 8080
```

You'll get output like:
```
Forwarding  https://abc123.ngrok-free.app -> http://localhost:8080
```

Use this URL in GitHub webhook settings:
```
https://abc123.ngrok-free.app/api/webhooks/github
```

---

## Verify Webhook in GitHub

1. Go to: https://github.com/Vipulpawar7045/mini-vercel-test-site/settings/hooks
2. Click on your webhook
3. Scroll to "Recent Deliveries"
4. Click on any delivery to see:
   - Request headers
   - Request payload
   - Response from your server

Green checkmark = success (200 OK)
Red X = failure (check response for error message)

---

## Check Logs

Watch Spring Boot logs for:
```
Received push event for repo: https://github.com/..., commit: abc123
Triggered deployment ... for project ...
Starting build for deployment ...
```

---

## Phase 5: Rate Limiting & Quotas

### Test Deployment Rate Limit

```bash
# Trigger 11 deployments rapidly (should hit limit on 11th)
for i in {1..11}; do
  curl -X POST http://localhost:8080/api/projects/{projectId}/deployments \
    -H "Authorization: Bearer YOUR_JWT_TOKEN"
  echo "\n--- Deployment $i ---"
  sleep 1
done
```

Expected on 11th request (HTTP 429):
```json
{
  "error": "rate_limit_exceeded",
  "message": "Hourly deployment limit exceeded (10/10). Please try again later."
}
```

### Test Active Deployment Limit

```bash
# Start 4 long-running deployments (should hit limit on 4th)
for i in {1..4}; do
  curl -X POST http://localhost:8080/api/projects/{projectId}/deployments \
    -H "Authorization: Bearer YOUR_JWT_TOKEN"
  echo "\n--- Active deployment $i ---"
done
```

Expected on 4th request (HTTP 429):
```json
{
  "error": "rate_limit_exceeded",
  "message": "Too many active deployments. Please wait for some to complete before starting new ones."
}
```

### Test Project Quota

```bash
# Create 11 projects (should hit quota on 11th)
for i in {1..11}; do
  curl -X POST http://localhost:8080/api/projects \
    -H "Authorization: Bearer YOUR_JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"project-$i\",
      \"gitRepoUrl\": \"https://github.com/user/repo-$i.git\",
      \"buildCommand\": \"npm run build\",
      \"outputDir\": \"dist\"
    }"
  echo "\n--- Project $i ---"
done
```

Expected on 11th request (HTTP 429):
```json
{
  "error": "rate_limit_exceeded",
  "message": "Project limit reached (10/10). Delete some projects before creating new ones."
}
```

### Test Deployment Cancellation

```bash
# Start a deployment
RESPONSE=$(curl -X POST http://localhost:8080/api/projects/{projectId}/deployments \
  -H "Authorization: Bearer YOUR_JWT_TOKEN")
DEPLOY_ID=$(echo $RESPONSE | jq -r '.deploymentId')
echo "Started deployment: $DEPLOY_ID"

# Cancel it immediately
curl -X POST http://localhost:8080/api/projects/{projectId}/deployments/$DEPLOY_ID/cancel \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Testing cancellation feature"}'
```

Response:
```json
{
  "deploymentId": "...",
  "status": "cancelled",
  "errorMessage": "Cancelled by user: Testing cancellation feature",
  "finishedAt": "2026-08-09T10:35:00Z"
}
```

### Try to Cancel a Completed Deployment

```bash
# Should fail with error
curl -X POST http://localhost:8080/api/projects/{projectId}/deployments/{completedDeploymentId}/cancel \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Too late"}'
```

Expected (HTTP 500):
```json
{
  "error": "internal_server_error",
  "message": "Cannot cancel a deployment that is already success"
}
```

### Test Admin Endpoints

```bash
# Get system statistics
curl http://localhost:8080/api/admin/stats
```

Response:
```json
{
  "totalUsers": 5,
  "totalProjects": 12,
  "totalDeployments": 48,
  "deploymentsLastHour": 3,
  "activeDeployments": 2
}
```

```bash
# List active deployments
curl http://localhost:8080/api/admin/active-deployments
```

Response:
```json
[
  {
    "deploymentId": "abc-123",
    "projectId": "proj-456",
    "projectName": "my-site",
    "status": "building",
    "createdAt": "2026-08-09T10:30:00Z"
  },
  {
    "deploymentId": "def-789",
    "projectId": "proj-456",
    "projectName": "my-site",
    "status": "queued",
    "createdAt": "2026-08-09T10:31:00Z"
  }
]
```

---

## Troubleshooting

### "No project configured for this repository"
- Check project's `gitRepoUrl` matches webhook payload exactly
- List projects: `GET /api/projects` with your JWT token
- GitHub sends `clone_url` which includes `.git` — store URL the same way

### "Invalid signature"
- Make sure `GITHUB_WEBHOOK_SECRET` env var matches GitHub webhook secret
- In dev mode (no secret set), signature check is skipped

### Build fails
- Check build logs in deployment record
- Verify repo is public or you have SSH keys configured
- Confirm `buildCommand` and `outputDir` are correct for your project

### Deployment stuck in "queued"
- Check if build is actually running (Spring logs)
- Verify async execution is enabled (it should be by default)
- Check for exceptions in logs
