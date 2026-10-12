import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

export function getConfigPath() {
  if (process.env.HOSTY_CONFIG_PATH) {
    return process.env.HOSTY_CONFIG_PATH;
  }
  if (process.env.MINIVERCEL_CONFIG_PATH) {
    return process.env.MINIVERCEL_CONFIG_PATH;
  }
  const homeDir = os.homedir();
  const hostyPath = path.join(homeDir, '.hosty', 'config.json');
  if (fs.existsSync(hostyPath)) {
    return hostyPath;
  }
  const legacyPath = path.join(homeDir, '.minivercel', 'config.json');
  if (fs.existsSync(legacyPath)) {
    return legacyPath;
  }
  return hostyPath;
}

export function loadConfig() {
  try {
    const filePath = getConfigPath();
    if (!fs.existsSync(filePath)) {
      return {};
    }
    const content = fs.readFileSync(filePath, 'utf-8');
    return JSON.parse(content);
  } catch (err) {
    return {};
  }
}

export function saveConfig(data) {
  const filePath = getConfigPath();
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  const current = loadConfig();
  const updated = { ...current, ...data };
  fs.writeFileSync(filePath, JSON.stringify(updated, null, 2), 'utf-8');
  return updated;
}

export function clearConfig() {
  const filePath = getConfigPath();
  if (fs.existsSync(filePath)) {
    fs.unlinkSync(filePath);
  }
}

export function getApiUrl() {
  if (process.env.HOSTY_API_URL) {
    return process.env.HOSTY_API_URL.replace(/\/+$/, '');
  }
  if (process.env.MINIVERCEL_API_URL) {
    return process.env.MINIVERCEL_API_URL.replace(/\/+$/, '');
  }
  const cfg = loadConfig();
  return (cfg.apiUrl || 'http://localhost:8080').replace(/\/+$/, '');
}

export function getToken() {
  if (process.env.HOSTY_TOKEN) {
    return process.env.HOSTY_TOKEN;
  }
  if (process.env.MINIVERCEL_TOKEN) {
    return process.env.MINIVERCEL_TOKEN;
  }
  const cfg = loadConfig();
  return cfg.token || null;
}
