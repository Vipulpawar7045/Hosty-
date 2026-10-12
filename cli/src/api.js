/**
 * Mini-Vercel API client wrapping standard HTTP fetch.
 */

async function request(url, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  const response = await fetch(url, {
    ...options,
    headers
  });

  const text = await response.text();
  let json = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = text;
    }
  }

  if (!response.ok) {
    const errorMsg = (json && typeof json === 'object' && json.message)
      ? json.message
      : (json && typeof json === 'object' && json.error)
        ? json.error
        : `HTTP error ${response.status}: ${response.statusText}`;
    const err = new Error(errorMsg);
    err.status = response.status;
    err.data = json;
    throw err;
  }

  return json;
}

export const api = {
  async register(apiUrl, email, password) {
    return request(`${apiUrl}/api/auth/register`, {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
  },

  async login(apiUrl, email, password) {
    return request(`${apiUrl}/api/auth/login`, {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
  },

  async listProjects(apiUrl, token) {
    return request(`${apiUrl}/api/projects`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` }
    });
  },

  async getProject(apiUrl, token, projectId) {
    return request(`${apiUrl}/api/projects/${projectId}`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` }
    });
  },

  async createProject(apiUrl, token, data) {
    return request(`${apiUrl}/api/projects`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify(data)
    });
  },

  async deleteProject(apiUrl, token, projectId) {
    return request(`${apiUrl}/api/projects/${projectId}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` }
    });
  },

  async triggerDeployment(apiUrl, token, projectId, options = {}) {
    return request(`${apiUrl}/api/projects/${projectId}/deployments`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        branch: options.branch || null,
        commitSha: options.commitSha || null
      })
    });
  },

  async getDeployment(apiUrl, token, projectId, deploymentId) {
    return request(`${apiUrl}/api/projects/${projectId}/deployments/${deploymentId}`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` }
    });
  },

  async listDeployments(apiUrl, token, projectId) {
    return request(`${apiUrl}/api/projects/${projectId}/deployments`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` }
    });
  },

  async rollbackDeployment(apiUrl, token, projectId, deploymentId) {
    return request(`${apiUrl}/api/projects/${projectId}/deployments/${deploymentId}/rollback`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` }
    });
  },

  async cancelDeployment(apiUrl, token, projectId, deploymentId, reason) {
    return request(`${apiUrl}/api/projects/${projectId}/deployments/${deploymentId}/cancel`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({ reason: reason || 'Cancelled by CLI user' })
    });
  },

  async listDomains(apiUrl, token, projectId) {
    return request(`${apiUrl}/api/projects/${projectId}/domains`, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` }
    });
  },

  async addDomain(apiUrl, token, projectId, domain) {
    return request(`${apiUrl}/api/projects/${projectId}/domains`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({ domain })
    });
  },

  async removeDomain(apiUrl, token, projectId, domain) {
    return request(`${apiUrl}/api/projects/${projectId}/domains/${domain}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` }
    });
  }
};
