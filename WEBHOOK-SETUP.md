# GitHub Webhook Setup Guide

This guide walks through setting up GitHub webhooks to auto-trigger deployments on every `git push`.

---

## How It Works

1. Developer pushes code to their GitHub repo
2. GitHub sends a webhook POST request to your Spring Boot server
3. Server verifies the HMAC-SHA256 signature
4. Server looks up the project by repo URL
5. Server triggers a deployment automatically

---

## Local Development Setup

### 1. Expose localhost with ngrok

GitHub webhooks need a public URL to reach your local server. Use ngrok to create a tunnel:

```bash
# Install ngrok from https://ngrok.com/download

# Start ngrok tunnel to port 8080
ngrok http 8080
```

You'll get a forwarding URL like: `https://abcd-1234-5678.ngrok-free.app`

### 2. Configure Webhook Secret

Set a webhook secret in your environment:

```bash
# Windows
set GITHUB_WEBHOOK_SECRET=your-secret-here-use-strong-random-value

# Linux/Mac
export GITHUB_WEBHOOK_SECRET=your-secret-here-use-strong-random-value
```

Or add it to `application.yml`:
```yaml
minivercel:
  webhook:
    github-secret: your-secret-here
```

### 3. Start the Spring Boot Server

```bash
cd C:\Users\Admin\OneDrive\Desktop\cloud\mini-vercel
java -jar target\mini-vercel-0.0.1-SNAPSHOT.jar
```

Or rebuild and run:
```bash
./mvnw clean package -DskipTests
java -jar target\mini-vercel-0.0.1-SNAPSHOT.jar
```

---

## GitHub Repository Configuration

### 1. Register the Webhook

1. Go to your GitHub repo: https://github.com/Vipulpawar7045/mini-vercel-test-site
2. Click **Settings** → **Webhooks** → **Add webhook**
3. Configure:
   - **Payload URL**: `https://your-ngrok-url.ngrok-free.app/api/webhooks/github`
   - **Content type**: `application/json`
   - **Secret**: Same value as `GITHUB_WEBHOOK_SECRET`
   - **Which events**: Select "Just the push event"
   - **Active**: ✓ Checked
4. Click **Add webhook**

### 2. Test the Webhook

GitHub shows a list of recent deliveries. Click on any delivery to see:
- Request headers (including `X-Hub-Signature-256`)
- Request body (the push event payload)
- Response from your server

---

## API Endpoints

### Webhook Endpoint

```http
POST /api/webhooks/github
Content-Type: application/json
X-Hub-Signature-256: sha256=<hmac-signature>
X-GitHub-Event: push

{
  "repository": {
    "clone_url": "https://github.com/user/repo.git",
    "html_url": "https://github.com/user/repo"
  },
  "head_commit": {
    "id": "abc123..."
  },
  "after": "abc123..."
}
```

### Manual Deployment (still works)

```http
POST /api/deployments
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "projectId": "your-project-id"
}
```

---

## How Repo Matching Works

The webhook handler looks up projects by `gitRepoUrl`. Make sure your project's repo URL matches one of:
- `repository.clone_url` (e.g., `https://github.com/user/repo.git`)
- `repository.html_url` (e.g., `https://github.com/user/repo`)

Check your project's stored URL:
```bash
# If you stored: https://github.com/Vipulpawar7045/mini-vercel-test-site
# GitHub sends:   https://github.com/Vipulpawar7045/mini-vercel-test-site.git

# Solution: normalize URLs (strip .git suffix) or store both formats
```

---

## Signature Verification

The webhook endpoint verifies GitHub's HMAC-SHA256 signature to prevent unauthorized requests.

**Security note**: If `GITHUB_WEBHOOK_SECRET` is not set, the endpoint **allows all requests** (dev mode only). Always set the secret in production.

How it works:
1. GitHub computes: `HMAC-SHA256(webhook_secret, request_body)`
2. GitHub sends result in header: `X-Hub-Signature-256: sha256=<hex>`
3. Server recomputes the same HMAC
4. Server compares using constant-time equality check

---

## Troubleshooting

### Webhook fails with "Invalid signature"
- Check that `GITHUB_WEBHOOK_SECRET` matches the secret configured in GitHub
- Verify the secret doesn't have leading/trailing spaces
- Check ngrok is forwarding requests correctly

### "No project configured for this repository"
- Verify the project's `gitRepoUrl` exactly matches the URL GitHub sends
- Check with: `GET /api/projects` (with your JWT token)
- GitHub sends `clone_url` (with `.git`) — your stored URL must match

### Deployment not triggering
- Check Spring Boot logs for errors
- Verify the webhook endpoint is public (not behind JWT auth)
- Test the endpoint manually with curl:
```bash
curl -X POST https://your-ngrok-url.ngrok-free.app/api/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -d '{"repository":{"clone_url":"https://github.com/user/repo.git"},"after":"abc123"}'
```

### ngrok URL keeps changing
- Free ngrok URLs expire and change on restart
- Upgrade to ngrok paid plan for a static URL, or
- Use a cloud deployment (AWS EC2, Lambda Function URL, etc.)

---

## Production Deployment

For production, replace ngrok with a permanent setup:

### Option 1: EC2 with Elastic IP
- Deploy Spring Boot on EC2
- Assign an Elastic IP
- Point webhook to `http://<elastic-ip>:8080/api/webhooks/github`

### Option 2: Lambda + Function URL
- Package Spring Boot as a Lambda function
- Enable Function URL (public endpoint)
- Point webhook to the Function URL

### Option 3: API Gateway + Lambda
- Deploy Spring Boot as Lambda
- Create API Gateway REST API
- Point webhook to API Gateway endpoint

---

## Next Steps (Phase 5)

- [ ] Add rate limiting per project (max deployments per hour)
- [ ] Add per-user access control validation in webhook handler
- [ ] Implement deployment queue with SQS (decouple webhook from build)
- [ ] Add webhook delivery status tracking in database
- [ ] Support GitLab and Bitbucket webhooks (not just GitHub)
