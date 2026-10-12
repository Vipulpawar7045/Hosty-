# Phase 5 Implementation Summary

## What Was Built

Phase 5 adds access control, rate limiting, quotas, deployment cancellation, and admin monitoring endpoints.

### New Components

#### 1. Rate Limiting (`com.minivercel.ratelimit`)

**RateLimitService**
- Checks deployment rate limits before creating new deployments
- Two limits enforced:
  - **Active deployments**: Max 3 concurrent (queued/building) deployments per project
  - **Hourly limit**: Max 10 deployments per project per hour
- Throws `RateLimitException` when limits are exceeded

#### 2. Quota Management (`com.minivercel.quota`)

**QuotaService**
- Enforces user-level quotas
- **Project quota**: Max 10 projects per user
- Checked before creating new projects
- Throws `RateLimitException` when quota is exceeded

#### 3. Deployment Cancellation

**New Features**:
- `POST /api/projects/{projectId}/deployments/{deploymentId}/cancel` endpoint
- Cancel queued or building deployments
- Cannot cancel completed/failed/already-cancelled deployments
- Requires user ownership
- Records cancellation reason in deployment record

**New DTOs**:
- `CancelDeploymentRequest` — requires `reason` field
- `DeploymentStatus.CANCELLED` — new status enum value

#### 4. Admin Monitoring (`com.minivercel.admin`)

**AdminController** — `/api/admin/**` endpoints

**GET /api/admin/stats**
```json
{
  "totalUsers": 5,
  "totalProjects": 12,
  "totalDeployments": 48,
  "deploymentsLastHour": 3,
  "activeDeployments": 2
}
```

**GET /api/admin/active-deployments**
```json
[
  {
    "deploymentId": "...",
    "projectId": "...",
    "projectName": "my-site",
    "status": "building",
    "createdAt": "2026-08-09T10:30:00Z"
  }
]
```

**Note**: Admin endpoints are currently public — production should add role-based auth.

### Configuration Updates

**application.yml** — New `limits` section:
```yaml
minivercel:
  limits:
    max-projects-per-user: 10
    max-deployments-per-hour: 10
    max-active-deployments: 3
```

**AppProperties.java** — New `Limits` record:
```java
public record Limits(
    int maxProjectsPerUser,
    int maxDeploymentsPerHour,
    int maxActiveDeployments)
```

### Exception Handling

**RateLimitException** — New exception type
- HTTP 429 (Too Many Requests)
- Handled by `ApiExceptionHandler`
- Returns structured error with clear message

Example response:
```json
{
  "error": "rate_limit_exceeded",
  "message": "Hourly deployment limit exceeded (10/10). Please try again later."
}
```

### Files Created

1. **RateLimitException.java** — Exception for quota/limit violations
2. **RateLimitService.java** — Deployment rate limiting logic
3. **QuotaService.java** — User quota enforcement
4. **CancelDeploymentRequest.java** — DTO for cancellation requests
5. **AdminController.java** — Admin monitoring endpoints

### Files Modified

1. **application.yml** — Added limits configuration
2. **AppProperties.java** — Added Limits record
3. **ApiExceptionHandler.java** — Added RateLimitException handler
4. **DeploymentStatus.java** — Added CANCELLED status
5. **DeploymentService.java** — Added rate limit checks and cancel() method
6. **DeploymentController.java** — Added cancel endpoint
7. **ProjectService.java** — Added quota checks
8. **SecurityConfig.java** — Whitelisted admin endpoints
9. **UserRepository.java** — Added findAll() method
10. **ProjectRepository.java** — Added findAll() method

### How It Works

#### Rate Limiting Flow
```
User triggers deployment
        ↓
DeploymentService.enqueue()
        ↓
RateLimitService.checkDeploymentLimits()
        ↓
Check active deployments (queued/building)
        ↓
Check hourly limit (deployments in last hour)
        ↓
If OK: create deployment
If exceeded: throw RateLimitException (HTTP 429)
```

#### Quota Enforcement Flow
```
User creates project
        ↓
ProjectService.create()
        ↓
QuotaService.checkProjectQuota()
        ↓
Count user's existing projects
        ↓
If under limit: create project
If at limit: throw RateLimitException (HTTP 429)
```

#### Deployment Cancellation Flow
```
User calls POST .../cancel
        ↓
Check ownership
        ↓
Check status (must be queued or building)
        ↓
Update status to CANCELLED
        ↓
Record reason in errorMessage field
        ↓
Set finishedAt timestamp
```

### Testing

#### Test Rate Limits

```bash
# Trigger 11 deployments rapidly (should hit hourly limit on 11th)
for i in {1..11}; do
  curl -X POST http://localhost:8080/api/projects/{projectId}/deployments \
    -H "Authorization: Bearer $TOKEN"
  echo "\nDeployment $i"
done
```

Expected on 11th request:
```json
{
  "error": "rate_limit_exceeded",
  "message": "Hourly deployment limit exceeded (10/10). Please try again later."
}
```

#### Test Project Quota

```bash
# Create 11 projects (should hit quota on 11th)
for i in {1..11}; do
  curl -X POST http://localhost:8080/api/projects \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"project-$i\",
      \"gitRepoUrl\": \"https://github.com/user/repo-$i.git\",
      \"buildCommand\": \"npm run build\",
      \"outputDir\": \"dist\"
    }"
  echo "\nProject $i"
done
```

#### Test Deployment Cancellation

```bash
# Start a deployment
DEPLOY_ID=$(curl -X POST http://localhost:8080/api/projects/{projectId}/deployments \
  -H "Authorization: Bearer $TOKEN" | jq -r '.deploymentId')

# Cancel it
curl -X POST http://localhost:8080/api/projects/{projectId}/deployments/$DEPLOY_ID/cancel \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Testing cancellation"}'
```

Response:
```json
{
  "deploymentId": "...",
  "status": "cancelled",
  "errorMessage": "Cancelled by user: Testing cancellation",
  "finishedAt": "2026-08-09T10:35:00Z"
}
```

#### Test Admin Endpoints

```bash
# Get system stats
curl http://localhost:8080/api/admin/stats

# List active deployments
curl http://localhost:8080/api/admin/active-deployments
```

### Security Considerations

#### Current State (Development)
- Admin endpoints are **public** — no authentication required
- Useful for local testing and demos

#### Production Requirements
- Add admin role to User model
- Check role in AdminController endpoints
- Use Spring Security's `@PreAuthorize("hasRole('ADMIN')")`
- Implement proper role-based access control (RBAC)

Example:
```java
@GetMapping("/stats")
@PreAuthorize("hasRole('ADMIN')")
public Map<String, Object> stats() { ... }
```

### Performance Notes

#### Table Scans
The following methods perform DynamoDB table scans:
- `UserRepository.findAll()`
- `ProjectRepository.findAll()`
- Rate limit checks iterate through recent deployments

**Impact**: Acceptable for small datasets (< 1000 items)

**Production Optimization**:
1. Add pagination to admin endpoints
2. Use DynamoDB's `Limit` parameter
3. Consider CloudWatch metrics instead of querying DynamoDB
4. Cache admin stats with Redis (refresh every 1-5 minutes)

#### Rate Limit Storage
Current implementation queries DynamoDB on every deployment.

**Alternatives for Production**:
1. **Redis**: Store counters with TTL (faster, cheaper)
2. **DynamoDB with TTL**: Add `expiresAt` attribute
3. **API Gateway**: Use built-in rate limiting

### Known Limitations

1. **No distributed rate limiting**: Each app instance has separate checks. Use Redis for shared state.

2. **No deployment process cancellation**: Cancelling a deployment updates the database but doesn't kill the running build process. Phase 6 should add build process tracking.

3. **Admin endpoints unprotected**: Add role-based auth before production.

4. **No rate limit headers**: HTTP response doesn't include `X-RateLimit-*` headers. Consider adding:
   ```
   X-RateLimit-Limit: 10
   X-RateLimit-Remaining: 7
   X-RateLimit-Reset: 1691580000
   ```

5. **No burst allowance**: Strict hourly limit without token bucket algorithm. Consider allowing short bursts.

### Configuration Examples

#### Relaxed Limits (Development)
```yaml
minivercel:
  limits:
    max-projects-per-user: 100
    max-deployments-per-hour: 50
    max-active-deployments: 10
```

#### Strict Limits (Free Tier)
```yaml
minivercel:
  limits:
    max-projects-per-user: 3
    max-deployments-per-hour: 5
    max-active-deployments: 1
```

#### Enterprise Limits
```yaml
minivercel:
  limits:
    max-projects-per-user: 1000
    max-deployments-per-hour: 100
    max-active-deployments: 20
```

---

## Next Steps (Phase 6)

Phase 6 will add **Serverless Functions** — the key differentiator:

- Deploy backend functions to AWS Lambda
- Return Function URL for each deployment
- Support Node.js, Python, Java runtimes
- API Gateway integration
- Environment variable management

See original spec:
> Phase 6 — Serverless functions: deploy a single backend function to Lambda, return its Function URL. (Key differentiator — do this before polish work.)

---

**Status**: Phase 5 complete ✓  
**New Endpoints**: 3 (cancel deployment, admin stats, active deployments)  
**New Limits**: 3 (project quota, hourly rate, active deployments)  
**Ready for**: Local testing and Phase 6 planning
