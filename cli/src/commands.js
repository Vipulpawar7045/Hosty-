import readline from 'node:readline';
import { api } from './api.js';
import { loadConfig, saveConfig, clearConfig, getApiUrl, getToken } from './config.js';

// ANSI color helpers
const colors = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  green: '\x1b[32m',
  blue: '\x1b[34m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  cyan: '\x1b[36m',
  magenta: '\x1b[35m'
};

function prompt(question) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
  });
  return new Promise(resolve => {
    rl.question(question, answer => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

function requireAuth() {
  const token = getToken();
  if (!token) {
    console.error(`${colors.red}Error: You are not logged in.${colors.reset}`);
    console.log(`Run ${colors.cyan}hosty login${colors.reset} to authenticate.`);
    process.exit(1);
  }
  return token;
}

export async function loginCommand(options = {}) {
  const apiUrl = options.apiUrl || getApiUrl();
  let email = options.email;
  let password = options.password;

  if (!email) {
    email = await prompt(`${colors.cyan}Email:${colors.reset} `);
  }
  if (!password) {
    password = await prompt(`${colors.cyan}Password:${colors.reset} `);
  }

  console.log(`Connecting to ${colors.dim}${apiUrl}${colors.reset}...`);
  try {
    const res = await api.login(apiUrl, email, password);
    saveConfig({
      apiUrl,
      token: res.token,
      userId: res.userId,
      email: res.email
    });
    console.log(`${colors.green}✓ Successfully logged in as ${colors.bold}${res.email}${colors.reset}`);
  } catch (err) {
    console.error(`${colors.red}✗ Login failed: ${err.message}${colors.reset}`);
    process.exit(1);
  }
}

export async function logoutCommand() {
  clearConfig();
  console.log(`${colors.green}✓ Logged out successfully. Removed stored credentials.${colors.reset}`);
}

export async function whoamiCommand() {
  const token = requireAuth();
  const apiUrl = getApiUrl();
  const cfg = loadConfig();

  try {
    const projects = await api.listProjects(apiUrl, token);
    console.log(`\n${colors.bold}Hosty User Information${colors.reset}`);
    console.log(`----------------------------------------`);
    console.log(`  ${colors.dim}Email:${colors.reset}     ${colors.green}${cfg.email || 'N/A'}${colors.reset}`);
    console.log(`  ${colors.dim}User ID:${colors.reset}   ${cfg.userId || 'N/A'}`);
    console.log(`  ${colors.dim}API Server:${colors.reset} ${apiUrl}`);
    console.log(`  ${colors.dim}Projects:${colors.reset}   ${projects.length}`);
    console.log();
  } catch (err) {
    console.error(`${colors.red}✗ Failed to fetch user info: ${err.message}${colors.reset}`);
    process.exit(1);
  }
}

export async function projectsCommand(action = 'list', args = {}, options = {}) {
  const token = requireAuth();
  const apiUrl = getApiUrl();

  if (action === 'list') {
    try {
      const projects = await api.listProjects(apiUrl, token);
      if (projects.length === 0) {
        console.log(`${colors.yellow}No projects found. Create one with: hosty projects create${colors.reset}`);
        return;
      }

      console.log(`\n${colors.bold}Your Projects (${projects.length})${colors.reset}`);
      console.log(`----------------------------------------------------------------------------------------------------`);
      console.log(
        `${'ID'.padEnd(38)} ${'NAME'.padEnd(20)} ${'BRANCH'.padEnd(12)} ${'REPO'}`
      );
      console.log(`----------------------------------------------------------------------------------------------------`);
      for (const p of projects) {
        console.log(
          `${p.projectId.padEnd(38)} ${colors.cyan}${p.name.padEnd(20)}${colors.reset} ${(p.productionBranch || 'main').padEnd(12)} ${p.gitRepoUrl}`
        );
      }
      console.log();
    } catch (err) {
      console.error(`${colors.red}✗ Failed to list projects: ${err.message}${colors.reset}`);
      process.exit(1);
    }
  } else if (action === 'create') {
    let name = options.name;
    let gitRepoUrl = options.repo || options.gitRepoUrl;
    let buildCommand = options.buildCommand || 'npm run build';
    let outputDir = options.outputDir || 'dist';
    let productionBranch = options.branch || options.productionBranch || 'main';

    if (!name) name = await prompt(`${colors.cyan}Project Name:${colors.reset} `);
    if (!gitRepoUrl) gitRepoUrl = await prompt(`${colors.cyan}Git Repository URL (https):${colors.reset} `);

    try {
      const created = await api.createProject(apiUrl, token, {
        name,
        gitRepoUrl,
        buildCommand,
        outputDir,
        productionBranch
      });
      console.log(`${colors.green}✓ Project created successfully!${colors.reset}`);
      console.log(`  Project ID: ${colors.bold}${created.projectId}${colors.reset}`);
      console.log(`  Name:       ${created.name}`);
      console.log(`  Repo:       ${created.gitRepoUrl}`);
      console.log(`  Branch:     ${created.productionBranch}`);
    } catch (err) {
      console.error(`${colors.red}✗ Failed to create project: ${err.message}${colors.reset}`);
      process.exit(1);
    }
  } else if (action === 'delete') {
    const projectId = args.projectId || options.projectId;
    if (!projectId) {
      console.error(`${colors.red}Usage: hosty projects delete <projectId>${colors.reset}`);
      process.exit(1);
    }
    try {
      await api.deleteProject(apiUrl, token, projectId);
      console.log(`${colors.green}✓ Project ${projectId} deleted successfully.${colors.reset}`);
    } catch (err) {
      console.error(`${colors.red}✗ Failed to delete project: ${err.message}${colors.reset}`);
      process.exit(1);
    }
  }
}

export async function deployCommand(projectId, options = {}) {
  const token = requireAuth();
  const apiUrl = getApiUrl();

  // If no projectId provided, list projects or auto-select if only 1
  if (!projectId) {
    const projects = await api.listProjects(apiUrl, token);
    if (projects.length === 0) {
      console.error(`${colors.red}No projects found to deploy. Create one first.${colors.reset}`);
      process.exit(1);
    }
    if (projects.length === 1) {
      projectId = projects[0].projectId;
      console.log(`Auto-selected project: ${colors.bold}${projects[0].name}${colors.reset} (${projectId})`);
    } else {
      console.log(`\nAvailable projects:`);
      projects.forEach((p, idx) => console.log(`  [${idx + 1}] ${p.name} (${p.projectId})`));
      const selection = await prompt(`Select project (1-${projects.length}): `);
      const idx = parseInt(selection, 10) - 1;
      if (idx >= 0 && idx < projects.length) {
        projectId = projects[idx].projectId;
      } else {
        console.error(`${colors.red}Invalid selection.${colors.reset}`);
        process.exit(1);
      }
    }
  }

  console.log(`\n${colors.cyan}🚀 Triggering deployment for project ${projectId}...${colors.reset}`);
  let deployment;
  try {
    deployment = await api.triggerDeployment(apiUrl, token, projectId, {
      branch: options.branch,
      commitSha: options.commit
    });
    console.log(`Deployment queued with ID: ${colors.bold}${deployment.deploymentId}${colors.reset}`);
    console.log(`Environment: ${colors.magenta}${deployment.environment}${colors.reset}`);
  } catch (err) {
    console.error(`${colors.red}✗ Failed to trigger deployment: ${err.message}${colors.reset}`);
    process.exit(1);
  }

  // Poll deployment status
  console.log(`\n${colors.yellow}⏳ Building and deploying... (polling status)${colors.reset}`);
  const startTime = Date.now();
  const deploymentId = deployment.deploymentId;

  while (true) {
    await new Promise(r => setTimeout(r, 1500));
    try {
      const current = await api.getDeployment(apiUrl, token, projectId, deploymentId);
      const status = current.status;
      const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);

      process.stdout.write(`\r[${elapsed}s] Status: ${colors.bold}${status.toUpperCase()}${colors.reset} `);

      if (status === 'success') {
        console.log(`\n\n${colors.green}✓ Deployment succeeded!${colors.reset}`);
        console.log(`--------------------------------------------------------------------------------`);
        if (current.deployUrl) {
          console.log(`  🌐 ${colors.bold}Static Deploy URL:${colors.reset}   ${colors.cyan}${current.deployUrl}${colors.reset}`);
        }
        if (current.functionUrl) {
          console.log(`  ⚡ ${colors.bold}Serverless API URL:${colors.reset}  ${colors.green}${current.functionUrl}${colors.reset}`);
        }
        console.log(`  🏷️  ${colors.dim}Environment:${colors.reset}        ${current.environment}`);
        console.log(`  📦 ${colors.dim}Deployment ID:${colors.reset}      ${current.deploymentId}`);
        console.log(`--------------------------------------------------------------------------------\n`);
        break;
      } else if (status === 'failed' || status === 'cancelled') {
        console.log(`\n\n${colors.red}✗ Deployment ${status}!${colors.reset}`);
        if (current.errorMessage) {
          console.error(`Error: ${current.errorMessage}`);
        }
        if (current.logs) {
          console.log(`\n--- Build Logs ---`);
          console.log(current.logs);
        }
        process.exit(1);
      }
    } catch (err) {
      console.warn(`\nWarning: Polling check failed (${err.message}). Retrying...`);
    }
  }
}

export async function rollbackCommand(projectId, deploymentId) {
  const token = requireAuth();
  const apiUrl = getApiUrl();

  if (!projectId || !deploymentId) {
    console.error(`${colors.red}Usage: hosty rollback <projectId> <targetDeploymentId>${colors.reset}`);
    process.exit(1);
  }

  console.log(`\n${colors.cyan}⚡ Initiating instant zero-rebuild rollback to deployment ${deploymentId}...${colors.reset}`);
  try {
    const rolledBack = await api.rollbackDeployment(apiUrl, token, projectId, deploymentId);
    console.log(`\n${colors.green}✓ Instant Rollback Completed in milliseconds!${colors.reset}`);
    console.log(`--------------------------------------------------------------------------------`);
    console.log(`  📦 ${colors.bold}New Deployment ID:${colors.reset}  ${rolledBack.deploymentId}`);
    console.log(`  🎯 ${colors.bold}Rolled Back Target:${colors.reset} ${deploymentId}`);
    if (rolledBack.deployUrl) {
      console.log(`  🌐 ${colors.bold}Live Deploy URL:${colors.reset}     ${colors.cyan}${rolledBack.deployUrl}${colors.reset}`);
    }
    if (rolledBack.functionUrl) {
      console.log(`  ⚡ ${colors.bold}Serverless API URL:${colors.reset}  ${colors.green}${rolledBack.functionUrl}${colors.reset}`);
    }
    console.log(`--------------------------------------------------------------------------------\n`);
  } catch (err) {
    console.error(`${colors.red}✗ Rollback failed: ${err.message}${colors.reset}`);
    process.exit(1);
  }
}

export async function deploymentsListCommand(projectId) {
  const token = requireAuth();
  const apiUrl = getApiUrl();

  if (!projectId) {
    console.error(`${colors.red}Usage: hosty deployments list <projectId>${colors.reset}`);
    process.exit(1);
  }

  try {
    const deployments = await api.listDeployments(apiUrl, token, projectId);
    console.log(`\n${colors.bold}Deployments for Project ${projectId} (${deployments.length})${colors.reset}`);
    console.log(`----------------------------------------------------------------------------------------------------`);
    console.log(
      `${'DEPLOYMENT ID'.padEnd(38)} ${'STATUS'.padEnd(12)} ${'ENV'.padEnd(12)} ${'CREATED AT'}`
    );
    console.log(`----------------------------------------------------------------------------------------------------`);
    for (const d of deployments) {
      const statusColor = d.status === 'success' ? colors.green : d.status === 'failed' ? colors.red : colors.yellow;
      console.log(
        `${d.deploymentId.padEnd(38)} ${statusColor}${d.status.padEnd(12)}${colors.reset} ${d.environment.padEnd(12)} ${d.createdAt}`
      );
    }
    console.log();
  } catch (err) {
    console.error(`${colors.red}✗ Failed to list deployments: ${err.message}${colors.reset}`);
    process.exit(1);
  }
}
