# Free-Tier Guarantee & Your Setup Steps

This project is engineered to run at **$0/month**. This doc is (1) proof every service stays free, and
(2) the exact steps you need to do on your side to see Phases 6 & 7 working on real AWS.

---

## Part 1 — Cost audit: why this stays at $0

| Service | What we use it for | Free-tier status | Notes |
|---|---|---|---|
| **DynamoDB** | users, projects, deployments | **Always free**: 25 GB storage + 25 WCU/RCU | Tables are `PAY_PER_REQUEST` (on-demand) → **zero cost when idle**. Our data is tiny. |
| **Lambda** | serverless functions (Phase 6) | **Always free**: 1M requests + 400k GB-sec/month | A demo function uses a rounding error of this. |
| **Lambda Function URLs** | public HTTP endpoint per function | **Free** — no charge for the URL itself | You only pay (from the free pool) when the function runs. |
| **S3** | built static sites (Phase 3) | 5 GB free for 12 months, then ~$0.023/GB | Our artifacts are a few MB. Even post-12-months this is fractions of a cent. |
| **CloudWatch Logs** | Lambda execution logs | **Always free**: 5 GB ingest + 5 GB storage | The `AWSLambdaBasicExecutionRole` writes here. |
| **IAM** | the Lambda execution role | **Always free** | Roles/policies never cost anything. |
| **Spring Boot API** | the backend itself | **Free** — runs on *your* machine | No EC2, no Fargate. This is the key cost-avoidance decision. |

### Things we deliberately AVOID (these cost money)
- **EC2 / RDS / Fargate** — only free for 12 months, or not free at all. We use your laptop + DynamoDB + Lambda instead.
- **NAT Gateway** — ~$32/mo if left running. We never create one.
- **CloudFront** — its free tier never expires (1 TB out, 10M requests), BUT your account needs verification
  to enable it. **Not required** — the app already serves sites via the free S3 website URL. (Phases 8–9 want it; they're parked.)
- **Route53** — ~$0.50/mo per hosted zone. This is the ONLY thing in the whole roadmap that isn't free,
  which is why **Phase 8 (custom domains) is optional/skipped**.

### The one safety net you should set (2 minutes, free)
Create a **$1 AWS Budget alert** so if anything ever goes sideways you get an email immediately:
1. AWS Console → **Billing** → **Budgets** → **Create budget**
2. Choose **Zero spend budget** (or a $1 cost budget), enter your email, create.
That's it — it costs nothing and is your insurance policy.

---

## Part 2 — What I already did for you (no action needed)

✅ **Created the Lambda execution role** — `arn:aws:iam::364660314424:role/mini-vercel-lambda-exec`
   with `AWSLambdaBasicExecutionRole` attached. (Free. Verified live.)
✅ **Wired that ARN as the default** in `application.yml`, so functions deploy with no env vars needed.
✅ **Rebuilt the jar** with that config.

---

## Part 3 — Your steps to test Phases 6 & 7 on real AWS

### A. Start the backend
```
cd C:\Users\Admin\OneDrive\Desktop\cloud\mini-vercel
java -jar target\mini-vercel-0.0.1-SNAPSHOT.jar
```
Runs on `http://localhost:8080`. Creates DynamoDB tables on first boot (real AWS, ap-south-1, on-demand = free).

> Windows/OneDrive note: if a rebuild ever fails with *"Unable to rename ...jar"*, a previous run is still
> holding the file. Fix: `tasklist | grep javaw`, then `taskkill //F //PID <pid>`, then rebuild.

### B. Register + create a project (get a JWT, then create a project)
```bash
# register (returns a token)
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"password123"}'

# save the token from the response, then:
TOKEN=<paste-token>

curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"demo","gitRepoUrl":"https://github.com/Vipulpawar7045/mini-vercel-test-site",
       "buildCommand":"npm ci && npm run build","outputDir":"dist","productionBranch":"main"}'
```

### C. Test Phase 6 (serverless function)
1. In your test repo `mini-vercel-test-site`, add a folder `api/` with `index.js`:
   ```js
   exports.handler = async () => ({
     statusCode: 200,
     headers: { "content-type": "application/json" },
     body: JSON.stringify({ message: "Hello from mini-vercel Lambda!" })
   });
   ```
2. Commit + push to `main`.
3. Trigger a deploy (either push a webhook, or POST to the deployments endpoint for the project).
4. Fetch the deployment — it will include a `functionUrl`. Open it. You should see the JSON above.
   - If it 403s: the public-invoke permission (`statementId: mv-public-invoke-url`) didn't attach — re-deploy.

### D. Test Phase 7 (preview deployments) — the headline feature
1. Create a branch: `git checkout -b feature/test-preview`, make a visible change, push it.
2. Trigger a deploy for that branch (via webhook push, or the API with the branch).
3. The deployment comes back with `environment: "preview"` and its own function `mv-<projectId>-feature-test-preview`.
   **Production is untouched.**
4. Now tear it down — any of:
   - delete the branch on GitHub (fires a `delete`/push webhook), OR
   - open+close a PR for it, OR
   - `DELETE http://localhost:8080/api/projects/{projectId}/deployments/preview?branch=feature/test-preview`
     with your `Authorization: Bearer $TOKEN`.
5. Verify: the preview Lambda is gone, its S3 files are gone, and the deployment record shows `status: removed`.
   Production still works.

### E. (Optional) Webhook automation
See `WEBHOOK-SETUP.md`. For local testing you'll need to expose port 8080 (e.g. a tunnel) so GitHub can reach it,
and set `GITHUB_WEBHOOK_SECRET` to match the secret you configure on the repo's webhook.

---

## Part 4 — What I can keep building without any AWS/paid infra
- **Phase 10 — CLI tool** (`mini-vercel deploy/login/logs`): pure client of the existing API. Fully free & testable.
- **Hardening**: more validation, integration tests, polished README leading with the two differentiators.

Both are $0 and need nothing from you. Phases 8 (custom domains, needs paid Route53) and 9 (rollback, needs
CloudFront) stay parked until you decide to unlock that infra.
