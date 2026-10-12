import { test, describe, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import http from 'node:http';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import { loadConfig, saveConfig, clearConfig, getConfigPath, getApiUrl, getToken } from '../src/config.js';
import { api } from '../src/api.js';

const execFileAsync = promisify(execFile);
const CLI_PATH = path.resolve('bin', 'hosty.js');

describe('Hosty CLI Test Suite', () => {
  const tmpDir = path.join(os.tmpdir(), 'mv-cli-test-' + Date.now());
  const testConfigFile = path.join(tmpDir, 'test-config.json');

  before(() => {
    fs.mkdirSync(tmpDir, { recursive: true });
    process.env.MINIVERCEL_CONFIG_PATH = testConfigFile;
  });

  after(() => {
    delete process.env.MINIVERCEL_CONFIG_PATH;
    try {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    } catch {}
  });

  beforeEach(() => {
    if (fs.existsSync(testConfigFile)) {
      fs.unlinkSync(testConfigFile);
    }
  });

  // ── 1. Config Manager Tests ──────────────────────────────────────────
  describe('Configuration Manager', () => {
    test('returns empty object when config file does not exist', () => {
      const cfg = loadConfig();
      assert.deepEqual(cfg, {});
    });

    test('saves and loads config correctly', () => {
      saveConfig({
        token: 'test-jwt-token-xyz',
        email: 'dev@example.com',
        userId: 'usr-123',
        apiUrl: 'http://custom-api:9000'
      });

      const loaded = loadConfig();
      assert.equal(loaded.token, 'test-jwt-token-xyz');
      assert.equal(loaded.email, 'dev@example.com');
      assert.equal(loaded.userId, 'usr-123');
      assert.equal(loaded.apiUrl, 'http://custom-api:9000');
    });

    test('clears config file', () => {
      saveConfig({ token: 'abc' });
      assert.equal(fs.existsSync(testConfigFile), true);

      clearConfig();
      assert.equal(fs.existsSync(testConfigFile), false);
      assert.deepEqual(loadConfig(), {});
    });

    test('retrieves API URL and token with fallbacks', () => {
      delete process.env.MINIVERCEL_API_URL;
      delete process.env.MINIVERCEL_TOKEN;

      assert.equal(getApiUrl(), 'http://localhost:8080');
      assert.equal(getToken(), null);

      saveConfig({ token: 'saved-token', apiUrl: 'http://my-host:8080/' });
      assert.equal(getApiUrl(), 'http://my-host:8080');
      assert.equal(getToken(), 'saved-token');
    });
  });

  // ── 2. API Client Unit Tests with Mock Server ───────────────────────
  describe('API Client', () => {
    let mockServer;
    let mockPort;
    let mockUrl;
    let lastRequest = null;

    before(async () => {
      mockServer = http.createServer((req, res) => {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
          lastRequest = {
            method: req.method,
            url: req.url,
            headers: req.headers,
            body: body ? JSON.parse(body) : null
          };

          if (req.url === '/api/auth/login' && req.method === 'POST') {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
              token: 'mock-jwt-token',
              tokenType: 'Bearer',
              expiresInSeconds: 3600,
              userId: 'u-1',
              email: 'test@example.com'
            }));
          } else if (req.url === '/api/projects' && req.method === 'GET') {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify([
              { projectId: 'p-1', name: 'demo-app', gitRepoUrl: 'https://github.com/test/demo' }
            ]));
          } else if (req.url === '/api/projects' && req.method === 'POST') {
            res.writeHead(201, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
              projectId: 'p-new',
              name: lastRequest.body.name,
              gitRepoUrl: lastRequest.body.gitRepoUrl
            }));
          } else if (req.url.endsWith('/rollback') && req.method === 'POST') {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
              deploymentId: 'd-rollback',
              status: 'success',
              deployUrl: 'http://s3.example.com/p-1/d-rollback/index.html'
            }));
          } else {
            res.writeHead(404, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: 'not_found', message: 'Resource not found' }));
          }
        });
      });

      await new Promise(resolve => mockServer.listen(0, resolve));
      mockPort = mockServer.address().port;
      mockUrl = `http://localhost:${mockPort}`;
    });

    after(async () => {
      if (mockServer) {
        await new Promise(resolve => mockServer.close(resolve));
      }
    });

    test('login sends correct credentials and parses auth response', async () => {
      const res = await api.login(mockUrl, 'user@test.com', 'password123');
      assert.equal(res.token, 'mock-jwt-token');
      assert.equal(res.email, 'test@example.com');
      assert.equal(lastRequest.body.email, 'user@test.com');
    });

    test('listProjects sends Authorization Bearer header', async () => {
      const projects = await api.listProjects(mockUrl, 'test-token');
      assert.equal(projects.length, 1);
      assert.equal(projects[0].projectId, 'p-1');
      assert.equal(lastRequest.headers.authorization, 'Bearer test-token');
    });

    test('createProject sends project payload', async () => {
      const created = await api.createProject(mockUrl, 'test-token', {
        name: 'my-site',
        gitRepoUrl: 'https://github.com/test/site'
      });
      assert.equal(created.projectId, 'p-new');
      assert.equal(created.name, 'my-site');
    });

    test('rollbackDeployment triggers rollback endpoint', async () => {
      const res = await api.rollbackDeployment(mockUrl, 'test-token', 'p-1', 'd-target');
      assert.equal(res.deploymentId, 'd-rollback');
      assert.equal(res.status, 'success');
      assert.match(lastRequest.url, /\/api\/projects\/p-1\/deployments\/d-target\/rollback/);
    });

    test('handles HTTP errors gracefully', async () => {
      await assert.rejects(
        async () => {
          await api.getProject(mockUrl, 'test-token', 'non-existent');
        },
        err => {
          assert.equal(err.status, 404);
          assert.match(err.message, /Resource not found/);
          return true;
        }
      );
    });
  });

  // ── 3. CLI Binary Execution Tests ──────────────────────────────────
  describe('CLI Binary Execution', () => {
    test('displays help message with --help', async () => {
      const { stdout } = await execFileAsync('node', [CLI_PATH, '--help']);
      assert.match(stdout, /Hosty CLI/);
      assert.match(stdout, /Commands:/);
      assert.match(stdout, /login/);
      assert.match(stdout, /deploy/);
      assert.match(stdout, /rollback/);
    });

    test('displays version with --version', async () => {
      const { stdout } = await execFileAsync('node', [CLI_PATH, '--version']);
      assert.match(stdout, /hosty-cli v1.0.0/);
    });

    test('fails with helpful message when not logged in', async () => {
      clearConfig();
      await assert.rejects(
        async () => {
          await execFileAsync('node', [CLI_PATH, 'whoami']);
        },
        err => {
          assert.match(err.stderr, /You are not logged in/);
          return true;
        }
      );
    });
  });

  // ── 4. End-to-End Live Backend Tests ────────────────────────────────
  describe('End-to-End Live Backend Integration', () => {
    function stripAnsi(str) {
      return str.replace(/\x1B\[[0-9;]*m/g, '');
    }

    test('performs login, whoami, and projects list against live localhost:8080', async () => {
      // 1. Run login command with flags
      const { stdout: loginOut } = await execFileAsync('node', [
        CLI_PATH,
        'login',
        '--email', 'admin@example.com',
        '--password', 'SuperSecretPassword123!',
        '--api-url', 'http://localhost:8080'
      ]);
      const cleanLogin = stripAnsi(loginOut);
      assert.match(cleanLogin, /Successfully logged in as admin@example\.com/);

      // Verify token saved in config
      const cfg = loadConfig();
      assert.ok(cfg.token);
      assert.equal(cfg.email, 'admin@example.com');

      // 2. Run whoami command
      const { stdout: whoamiOut } = await execFileAsync('node', [CLI_PATH, 'whoami']);
      const cleanWhoami = stripAnsi(whoamiOut);
      assert.match(cleanWhoami, /admin@example\.com/);
      assert.match(cleanWhoami, /Hosty User Information/);

      // 3. Run projects list command
      const { stdout: projectsOut } = await execFileAsync('node', [CLI_PATH, 'projects', 'list']);
      const cleanProjects = stripAnsi(projectsOut);
      assert.match(cleanProjects, /Your Projects/);
      assert.match(cleanProjects, /test-site/);

      // 4. Run deployments list command for the project
      const projects = await api.listProjects('http://localhost:8080', cfg.token);
      const proj = projects[0];
      assert.ok(proj);

      const { stdout: depsOut } = await execFileAsync('node', [CLI_PATH, 'deployments', 'list', proj.projectId]);
      const cleanDeps = stripAnsi(depsOut);
      assert.match(cleanDeps, /Deployments for Project/);
      assert.match(cleanDeps, /success/);

      // 5. Test rollback command via CLI
      const deployments = await api.listDeployments('http://localhost:8080', cfg.token, proj.projectId);
      const successfulTarget = deployments.find(d => d.status === 'success');
      assert.ok(successfulTarget, 'Should have a successful deployment to rollback');

      const { stdout: rollbackOut } = await execFileAsync('node', [
        CLI_PATH,
        'rollback',
        proj.projectId,
        successfulTarget.deploymentId
      ]);
      const cleanRollback = stripAnsi(rollbackOut);
      assert.match(cleanRollback, /Instant Rollback Completed in milliseconds!/);
      assert.match(cleanRollback, /New Deployment ID:/);
      assert.match(cleanRollback, /Live Deploy URL:/);
    });
  });
});
