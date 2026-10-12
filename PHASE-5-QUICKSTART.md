# Phase 5 Quick Start

## What's New

Phase 5 adds **rate limiting, quotas, and admin monitoring** to prevent abuse and provide visibility.

### New Limits

| Limit | Default | Config Key |
|-------|---------|------------|
| Projects per user | 10 | `minivercel.limits.max-projects-per-user` |
| Deployments per hour (per project) | 10 | `minivercel.limits.max-deployments-per-hour` |
| Active deployments (per project) | 3 | `minivercel.limits.max-active-deployments` |

### New Features

1. **Rate Limiting** — Returns HTTP 429 when limits are exceeded
2. **Deployment Cancellation** — Stop queued/building deployments
3. **Admin Dashboard** — Monitor system stats and active deployments

## Quick Test

### 1. Start the Server

```bash
cd C:\Users\Admin\OneDrive\Desktop\cloud\mini-vercel
java -jar target\mini-vercel-0.0.1-SNAPSHOT.jar
```

### 2. Get a JWT Token

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'

# Login (save the token)
TOKEN=$(curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}' | jq -r '.token')

echo $TOKEN
```

### 3. Create a Project

```bash
PROJECT_ID=$(curl -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-site",
    "gitRepoUrl": "https://github.com/Vipulpawar7045/mini-vercel-test-site.git",
    "buildCommand": "npm ci && npm run build",
    "outputDir": "dist"
  }' | jq -r '.projectId')

echo "Project created: $PROJECT_ID"
```

### 4. Test Rate Limiting

```bash
# Trigger 11 deployments (11th should fail)
for i in {1..11}; do
  echo "--- Deployment $i ---"
  curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/deployments \
    -H "Authorization: Bearer $TOKEN"
  echo ""
  sleep 1
done
```

Expected output on 11th request:
```json
{
  "error": "rate_limit_exceeded",
  "message": "Hourly deployment limit exceeded (10/10). Please try again later."
}
```

### 5. Test Deployment Cancellation

```bash
# Start a deployment
DEPLOY_ID=$(curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/deployments \
  -H "Authorization: Bearer $TOKEN" | jq -r '.deploymentId')

echo "Started deployment: $DEPLOY_ID"

# Cancel it
curl -X POST http://localhost:8080/api/projects/$PROJECT_ID/deployments/$DEPLOY_ID/cancel \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Testing cancellation"}'
```

### 6. Check Admin Stats

```bash
# Get system stats
curl http://localhost:8080/api/admin/stats | jq

# List active deployments
curl http://localhost:8080/api/admin/active-deployments | jq
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
minivercel:
  limits:
    max-projects-per-user: 10      # Increase for power users
    max-deployments-per-hour: 10   # Increase for CI/CD workloads
    max-active-deployments: 3      # Increase for parallel builds
```

### Example: Relaxed Limits (Development)

```yaml
minivercel:
  limits:
    max-projects-per-user: 100
    max-deployments-per-hour: 50
    max-active-deployments: 10
```

### Example: Strict Limits (Free Tier)

```yaml
minivercel:
  limits:
    max-projects-per-user: 3
    max-deployments-per-hour: 5
    max-active-deployments: 1
```

## API Reference

### New Endpoints

#### Cancel Deployment
```http
POST /api/projects/{projectId}/deployments/{deploymentId}/cancel
Authorization: Bearer <token>
Content-Type: application/json

{
  "reason": "User requested cancellation"
}
```

Response:
```json
{
  "deploymentId": "...",
  "status": "cancelled",
  "errorMessage": "Cancelled by user: User requested cancellation",
  "finishedAt": "2026-08-09T10:35:00Z"
}
```

#### Admin Stats
```http
GET /api/admin/stats
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

#### Admin Active Deployments
```http
GET /api/admin/active-deployments
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
  }
]
```

### Error Responses

#### Rate Limit Exceeded (HTTP 429)
```json
{
  "error": "rate_limit_exceeded",
  "message": "Hourly deployment limit exceeded (10/10). Please try again later."
}
```

#### Project Quota Exceeded (HTTP 429)
```json
{
  "error": "rate_limit_exceeded",
  "message": "Project limit reached (10/10). Delete some projects before creating new ones."
}
```

#### Cannot Cancel Deployment (HTTP 500)
```json
{
  "error": "internal_server_error",
  "message": "Cannot cancel a deployment that is already success"
}
```

## Known Issues

1. **Admin endpoints are public** — Add role-based auth before production
2. **Rate limits are per-instance** — Use Redis for distributed rate limiting
3. **Cancellation doesn't kill builds** — Only updates database status
4. **Table scans in admin endpoints** — Add pagination for large datasets

## Next Steps

Phase 6 will add **Serverless Functions**:
- Deploy backend functions to AWS Lambda
- Return Function URLs for each deployment
- Support Node.js, Python, Java runtimes

---

**Full documentation**: See `PHASE-5-SUMMARY.md` for implementation details
