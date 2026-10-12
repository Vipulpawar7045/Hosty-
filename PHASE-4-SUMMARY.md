# Phase 4 Implementation Summary

## What Was Built

Phase 4 adds GitHub webhook integration so every `git push` automatically triggers a deployment without manual API calls.

### New Components

1. **GitHubWebhookController** (`com.minivercel.webhook`)
   - `POST /api/webhooks/github` endpoint
   - HMAC-SHA256 signature verification using `X-Hub-Signature-256` header
   - Parses GitHub push events and extracts repo URL + commit SHA
   - Looks up project by repo URL and triggers deployment
   - Public endpoint (no JWT auth required)

2. **GitHubPushEvent** (`com.minivercel.webhook.dto`)
   - Parses GitHub webhook payload JSON
   - Extracts `repository.clone_url` and `head_commit.id`
   - Lightweight record class with static parse method

3. **Configuration Updates**
   - `AppProperties`: added `Webhook` record with `githubSecret` field
   - `application.yml`: added `minivercel.webhook.github-secret` config
   - `SecurityConfig`: added `/api/webhooks/**` to permitAll list

4. **Service Updates**
   - `DeploymentService.enqueue()`: now has overload accepting optional `commitSha` parameter
   - `ProjectRepository.findByGitRepoUrl()`: added method to look up project by repo URL (uses table scan)

### Files Modified

- `AppProperties.java` — added Webhook config record
- `application.yml` — added webhook secret config
- `SecurityConfig.java` — whitelisted webhook endpoint
- `DeploymentService.java` — added commitSha parameter support
- `ProjectRepository.java` — added findByGitRepoUrl method
- `GitHubWebhookController.java` — NEW
- `GitHubPushEvent.java` — NEW
- `WEBHOOK-SETUP.md` — NEW (comprehensive setup guide)

### How It Works

```
Developer pushes code
        ↓
GitHub webhook fires
        ↓
POST /api/webhooks/github
        ↓
Verify HMAC signature
        ↓
Parse push event (repo URL + commit SHA)
        ↓
Look up project by gitRepoUrl
        ↓
Call DeploymentService.enqueue(projectId, userId, commitSha)
        ↓
Async build pipeline starts (same as manual deployment)
```

### Security

- **HMAC-SHA256 verification**: GitHub signs every webhook request; server verifies using shared secret
- **Constant-time comparison**: uses `MessageDigest.isEqual()` to prevent timing attacks
- **Dev mode fallback**: if `github-secret` is not configured, signature verification is skipped (logs a warning)

### Testing Locally

1. Start the server: `java -jar target/mini-vercel-0.0.1-SNAPSHOT.jar`
2. Expose with ngrok: `ngrok http 8080`
3. Configure GitHub webhook pointing to `https://your-ngrok-url.ngrok-free.app/api/webhooks/github`
4. Push code to the repo
5. Check Spring Boot logs for deployment trigger

See `WEBHOOK-SETUP.md` for complete setup instructions.

### Known Limitations

1. **Table scan for repo lookup**: `findByGitRepoUrl()` scans all projects — inefficient for large datasets. Production should add a GSI (Global Secondary Index) on `gitRepoUrl` in DynamoDB.

2. **URL format matching**: GitHub sends `clone_url` (with `.git` suffix). Projects must store the exact same URL format, or we need normalization logic.

3. **No rate limiting yet**: A malicious actor could spam webhook requests. Phase 5 will add per-project rate limits.

4. **No delivery status tracking**: Webhook outcomes aren't persisted. Consider adding a `WebhookDelivery` table to track success/failure/retry.

### Next Steps (Phase 5)

- Rate limiting per project (max deployments per hour)
- Deployment queue with SQS (decouple webhook from build)
- Support for GitLab and Bitbucket webhooks
- Add GSI on `gitRepoUrl` for efficient lookups
- Webhook delivery status tracking

---

**Status**: Phase 4 complete ✓  
**Compiled**: Successfully with `./mvnw compile`  
**Ready for**: Local testing with ngrok + GitHub webhook registration
