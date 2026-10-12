#!/usr/bin/env node

import {
  loginCommand,
  logoutCommand,
  whoamiCommand,
  projectsCommand,
  deployCommand,
  rollbackCommand,
  deploymentsListCommand
} from '../src/commands.js';

const args = process.argv.slice(2);
const command = args[0];

function parseFlags(tokens) {
  const flags = {};
  const positional = [];

  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if (token.startsWith('--')) {
      const key = token.slice(2);
      const next = tokens[i + 1];
      if (next && !next.startsWith('--')) {
        flags[key] = next;
        i++;
      } else {
        flags[key] = true;
      }
    } else if (token.startsWith('-')) {
      const key = token.slice(1);
      const next = tokens[i + 1];
      if (next && !next.startsWith('-')) {
        flags[key] = next;
        i++;
      } else {
        flags[key] = true;
      }
    } else {
      positional.push(token);
    }
  }

  return { flags, positional };
}

function printHelp() {
  console.log(`
Hosty CLI 🚀

Usage:
  hosty <command> [arguments] [options]

Commands:
  login                        Authenticate with Hosty
  logout                       Clear stored credentials
  whoami                       Display currently authenticated user
  projects list                List all projects
  projects create              Create a new project
  projects delete <projectId>  Delete a project
  deploy [projectId]           Deploy a project and follow live build
  deployments list <projectId> List past deployments for a project
  rollback <projectId> <id>    Instantly roll back to a previous deployment

Options:
  --api-url <url>              Override Hosty API URL (default: http://localhost:8080)
  --email <email>              Email for login
  --password <pwd>             Password for login
  --branch <branch>            Git branch to deploy (e.g. main, feature/test)
  --commit <sha>               Commit SHA to tag
  --name <name>                Project name for projects create
  --repo <url>                 Git HTTPS URL for projects create
  -h, --help                   Display this help message
  -v, --version                Display version
`);
}

async function main() {
  if (!command || command === '--help' || command === '-h' || command === 'help') {
    printHelp();
    return;
  }

  if (command === '--version' || command === '-v' || command === 'version') {
    console.log('hosty-cli v1.0.0');
    return;
  }

  const { flags, positional } = parseFlags(args.slice(1));

  try {
    switch (command) {
      case 'login':
        await loginCommand(flags);
        break;

      case 'logout':
        await logoutCommand();
        break;

      case 'whoami':
        await whoamiCommand();
        break;

      case 'projects': {
        const subaction = positional[0] || 'list';
        const projectId = positional[1];
        await projectsCommand(subaction, { projectId }, flags);
        break;
      }

      case 'deploy': {
        const projectId = positional[0];
        await deployCommand(projectId, flags);
        break;
      }

      case 'deployments': {
        const subaction = positional[0] || 'list';
        const projectId = positional[1];
        if (subaction === 'list') {
          await deploymentsListCommand(projectId);
        } else {
          console.error(`Unknown deployments action: ${subaction}`);
        }
        break;
      }

      case 'rollback': {
        const projectId = positional[0];
        const deploymentId = positional[1];
        await rollbackCommand(projectId, deploymentId);
        break;
      }

      default:
        console.error(`Unknown command: ${command}`);
        printHelp();
        process.exit(1);
    }
  } catch (err) {
    console.error(`Error: ${err.message}`);
    process.exit(1);
  }
}

main();
