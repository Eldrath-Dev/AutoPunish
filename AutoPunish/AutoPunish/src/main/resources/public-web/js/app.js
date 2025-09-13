document.addEventListener('DOMContentLoaded', () => {
  const mainContent = document.getElementById('main-content');
  const navLinks = document.querySelectorAll('.nav-link');
  const API_URL = '/api/punishments';
  let refreshInterval;
  let currentUser = null;
  let chatRefreshInterval = null;

  // Check authentication status on load
  checkAuthStatus();

  // --- Authentication ---
  async function checkAuthStatus() {
    try {
      const response = await fetch('/api/auth/session');
      if (response.ok) {
        const data = await response.json();
        if (data.authenticated) {
          currentUser = data.user;
          updateNavbarForAuth(true);
        } else {
          currentUser = null;
          updateNavbarForAuth(false);
        }
      }
    } catch (error) {
      console.error('Error checking auth status:', error);
      currentUser = null;
      updateNavbarForAuth(false);
    }
  }

  function updateNavbarForAuth(isLoggedIn) {
    // Update navbar based on auth status
    const nav = document.querySelector('nav');
    if (nav) {
      // Remove existing auth-related links
      const existingAuthLinks = nav.querySelectorAll('[data-auth]');
      existingAuthLinks.forEach(link => link.remove());

      if (isLoggedIn) {
        // Add Staff Chat and Logout links
        const chatLink = document.createElement('a');
        chatLink.href = '#/staff-chat';
        chatLink.className = 'nav-link';
        chatLink.dataset.page = 'staff-chat';
        chatLink.dataset.auth = 'true';
        chatLink.innerHTML = '<i class="fas fa-comments"></i> Staff Chat';
        nav.appendChild(chatLink);

        const logoutLink = document.createElement('a');
        logoutLink.href = '#';
        logoutLink.className = 'nav-link';
        logoutLink.dataset.auth = 'true';
        logoutLink.innerHTML = '<i class="fas fa-sign-out-alt"></i> Logout';
        logoutLink.onclick = (e) => {
          e.preventDefault();
          logout();
        };
        nav.appendChild(logoutLink);
      } else {
        // Add Login link
        const loginLink = document.createElement('a');
        loginLink.href = '#/login';
        loginLink.className = 'nav-link';
        loginLink.dataset.page = 'login';
        loginLink.dataset.auth = 'true';
        loginLink.innerHTML = '<i class="fas fa-sign-in-alt"></i> Login';
        nav.appendChild(loginLink);
      }

      // Reattach event listeners to new links
      document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', function(e) {
          // Remove active class from all links
          document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
          // Add active class to clicked link
          this.classList.add('active');
        });
      });
    }
  }

  async function logout() {
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        }
      });
      currentUser = null;
      updateNavbarForAuth(false);
      window.location.hash = '#/';
      showMessage('Logged out successfully', 'success');
    } catch (error) {
      console.error('Logout error:', error);
      showMessage('Logout failed', 'error');
    }
  }

  // --- Router Logic ---
  function navigate() {
    if (refreshInterval) {
      clearInterval(refreshInterval);
      refreshInterval = null;
    }

    if (chatRefreshInterval) {
      clearInterval(chatRefreshInterval);
      chatRefreshInterval = null;
    }

    const hash = window.location.hash || '#/';
    const page = hash.substring(2) || 'home';

    // Update active nav links
    navLinks.forEach(link => {
      link.classList.toggle('active', link.dataset.page === page);
    });

    loadPageContent(page);

    if (['warns', 'mutes', 'bans'].includes(page)) {
      refreshInterval = setInterval(() => loadPageContent(page), 60000);
    } else if (page === 'staff-chat') {
      // Start chat refresh interval
      chatRefreshInterval = setInterval(() => loadStaffChat(), 10000); // Refresh every 10 seconds
    }
  }

  // --- Page Content Loading ---
  function loadPageContent(page) {
    mainContent.innerHTML = `
      <div class="page-content">
        <p class="loading"><i class="fas fa-spinner fa-spin"></i> Loading...</p>
      </div>`;

    switch (page) {
      case 'home':
        loadHomePage();
        break;
      case 'warns':
      case 'mutes':
      case 'bans':
        loadPunishmentsPage(page);
        break;
      case 'login':
        loadLoginPage();
        break;
      case 'staff-chat':
        loadStaffChatPage();
        break;
      default:
        mainContent.innerHTML = `
          <div class="page-content">
            <p class="error"><i class="fas fa-exclamation-triangle"></i> Page not found.</p>
          </div>`;
    }
  }

  function loadHomePage() {
    mainContent.innerHTML = `
      <div class="page-content">
        <h2><i class="fas fa-home"></i> Welcome to the Punishment Directory</h2>
        <div class="welcome-text">
          <p>This directory provides a public log of all punishments issued on our server. We believe in transparency and accountability for all moderation actions.</p>
          <p>Use the navigation links above to view specific types of punishments. The lists are updated automatically.</p>
          <div class="stats-summary">
            <h3><i class="fas fa-chart-bar"></i> Quick Stats</h3>
            <div id="quick-stats" class="stats-container">
              <p><i class="fas fa-spinner fa-spin"></i> Loading statistics...</p>
            </div>
          </div>
        </div>
      </div>`;

    loadQuickStats();
  }

  async function loadQuickStats() {
    try {
      const response = await fetch('/api/punishments/stats');
      if (response.ok) {
        const data = await response.json();
        const statsContainer = document.getElementById('quick-stats');
        if (statsContainer) {
          statsContainer.innerHTML = `
            <div class="stats-grid">
              <div class="stat-card">
                <div class="stat-value">${data.totalPunishments}</div>
                <div class="stat-label">Total Punishments</div>
              </div>
              <div class="stat-card">
                <div class="stat-value">${data.totalWarns}</div>
                <div class="stat-label">Warnings</div>
              </div>
              <div class="stat-card">
                <div class="stat-value">${data.totalMutes}</div>
                <div class="stat-label">Mutes</div>
              </div>
              <div class="stat-card">
                <div class="stat-value">${data.totalBans}</div>
                <div class="stat-label">Bans</div>
              </div>
            </div>
            <div class="recent-activity">
              <p><i class="fas fa-clock"></i> ${data.recentPunishments} punishments in the last 24 hours</p>
            </div>
          `;
        }
      }
    } catch (error) {
      console.error('Error loading stats:', error);
    }
  }

  // --- Login Page ---
  function loadLoginPage() {
    if (currentUser) {
      window.location.hash = '#/';
      return;
    }

    mainContent.innerHTML = `
      <div class="page-content">
        <h2><i class="fas fa-sign-in-alt"></i> Staff Login</h2>
        <div class="login-form-container">
          <form id="login-form" class="login-form">
            <div class="form-group">
              <label for="username"><i class="fas fa-user"></i> Username</label>
              <input type="text" id="username" name="username" required>
            </div>
            <div class="form-group">
              <label for="password"><i class="fas fa-lock"></i> Password</label>
              <input type="password" id="password" name="password" required>
            </div>
            <button type="submit" class="btn btn-primary">
              <i class="fas fa-sign-in-alt"></i> Login
            </button>
            <div id="login-message" class="form-message"></div>
          </form>
        </div>
      </div>
    `;

    document.getElementById('login-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('username').value;
      const password = document.getElementById('password').value;
      const messageEl = document.getElementById('login-message');

      try {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ username, password })
        });

        const data = await response.json();
        if (response.ok && data.success) {
          currentUser = data.user;
          updateNavbarForAuth(true);
          window.location.hash = '#/';
          showMessage('Login successful', 'success');
        } else {
          messageEl.innerHTML = `<p class="error"><i class="fas fa-exclamation-circle"></i> ${data.error || 'Login failed'}</p>`;
        }
      } catch (error) {
        messageEl.innerHTML = `<p class="error"><i class="fas fa-exclamation-circle"></i> Login failed: ${error.message}</p>`;
      }
    });
  }

  // --- Staff Chat Page ---
  function loadStaffChatPage() {
    if (!currentUser) {
      window.location.hash = '#/login';
      return;
    }

    mainContent.innerHTML = `
      <div class="page-content">
        <h2><i class="fas fa-comments"></i> Staff Chat</h2>
        <div class="chat-container">
          <div id="chat-messages" class="chat-messages">
            <p class="loading"><i class="fas fa-spinner fa-spin"></i> Loading messages...</p>
          </div>
          <div class="chat-input-container">
            <form id="chat-form">
              <input type="text" id="chat-message" placeholder="Type your message..." required>
              <button type="submit" class="btn btn-primary">
                <i class="fas fa-paper-plane"></i> Send
              </button>
            </form>
          </div>
        </div>
      </div>
    `;

    loadStaffChat();

    document.getElementById('chat-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const messageInput = document.getElementById('chat-message');
      const message = messageInput.value.trim();

      if (message) {
        try {
          const response = await fetch('/api/staff/chat', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ message })
          });

          if (response.ok) {
            messageInput.value = '';
            loadStaffChat(); // Refresh messages
          } else {
            const data = await response.json();
            showMessage(data.error || 'Failed to send message', 'error');
          }
        } catch (error) {
          showMessage('Failed to send message: ' + error.message, 'error');
        }
      }
    });
  }

  async function loadStaffChat() {
    if (!currentUser) return;

    try {
      const response = await fetch('/api/staff/chat?limit=50');
      if (response.ok) {
        const data = await response.json();
        const messagesContainer = document.getElementById('chat-messages');
        if (messagesContainer) {
          if (data.messages && data.messages.length > 0) {
            messagesContainer.innerHTML = data.messages.map(msg => `
              <div class="chat-message">
                <div class="message-header">
                  <span class="message-user">${escapeHtml(msg.staff_name)}</span>
                  <span class="message-time">${new Date(msg.timestamp).toLocaleString()}</span>
                </div>
                <div class="message-content">${escapeHtml(msg.message)}</div>
              </div>
            `).join('');
            // Scroll to bottom
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
          } else {
            messagesContainer.innerHTML = '<p class="no-messages">No messages yet. Be the first to send one!</p>';
          }
        }
      }
    } catch (error) {
      console.error('Error loading chat:', error);
    }
  }

  // --- Punishments Pages with Search ---
  async function loadPunishmentsPage(type) {
    try {
      // Build initial URL
      let url = `${API_URL}/${type}`;

      // Render page with search bar
      renderPunishmentsPage(type, [], true);

      // Load data
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      if (data.error) {
        throw new Error(data.error);
      }

      // Update table with data
      renderPunishmentsTable(type, data.punishments || [], false);
    } catch (error) {
      console.error(`Error fetching ${type}:`, error);
      mainContent.innerHTML = `
        <div class="page-content">
          <p class="error"><i class="fas fa-exclamation-triangle"></i> Could not load ${type}. Please try again later.</p>
          <p class="error-details"><i class="fas fa-info-circle"></i> Error: ${error.message}</p>
        </div>`;
    }
  }

  function renderPunishmentsPage(type, punishments, showLoading) {
    const typeTitle = type.charAt(0).toUpperCase() + type.slice(1);

    mainContent.innerHTML = `
      <div class="page-content">
        <h2><i class="fas fa-gavel"></i> All ${escapeHtml(typeTitle)}</h2>

        <!-- Search Bar -->
        <div class="search-container">
          <div class="search-box">
            <input type="text" id="search-player" placeholder="Search by player name..." class="search-input">
            <input type="text" id="search-rule" placeholder="Search by rule..." class="search-input">
            <button id="search-button" class="btn btn-secondary">
              <i class="fas fa-search"></i> Search
            </button>
            <button id="clear-search" class="btn btn-outline">
              <i class="fas fa-times"></i> Clear
            </button>
          </div>
        </div>

        <!-- Punishments Table -->
        <div class="table-container">
          <table class="punishments-table">
            <thead>
              <tr>
                <th>Player</th>
                <th>Rule</th>
                <th>Staff</th>
                <th>Date</th>
                <th>Duration</th>
                <th>Evidence</th>
                ${currentUser ? '<th>Actions</th>' : ''}
              </tr>
            </thead>
            <tbody id="punishments-tbody">
              ${showLoading ?
                `<tr><td colspan="${currentUser ? 7 : 6}" style="text-align: center;">
                  <p class="loading"><i class="fas fa-spinner fa-spin"></i> Loading...</p>
                </td></tr>` :
                ''
              }
            </tbody>
          </table>
        </div>
      </div>
    `;

    // Attach search event listeners
    document.getElementById('search-button').addEventListener('click', () => {
      performSearch(type);
    });

    document.getElementById('clear-search').addEventListener('click', () => {
      document.getElementById('search-player').value = '';
      document.getElementById('search-rule').value = '';
      performSearch(type);
    });

    // Allow Enter key to trigger search
    document.getElementById('search-player').addEventListener('keypress', (e) => {
      if (e.key === 'Enter') performSearch(type);
    });

    document.getElementById('search-rule').addEventListener('keypress', (e) => {
      if (e.key === 'Enter') performSearch(type);
    });
  }

  async function performSearch(type) {
    const playerFilter = document.getElementById('search-player').value.trim();
    const ruleFilter = document.getElementById('search-rule').value.trim();

    try {
      // Build search URL
      let url = `${API_URL}/${type}?`;
      const params = [];

      if (playerFilter) params.push(`player=${encodeURIComponent(playerFilter)}`);
      if (ruleFilter) params.push(`rule=${encodeURIComponent(ruleFilter)}`);

      url += params.join('&');

      // Show loading state
      const tbody = document.getElementById('punishments-tbody');
      tbody.innerHTML = `<tr><td colspan="${currentUser ? 7 : 6}" style="text-align: center;">
        <p class="loading"><i class="fas fa-spinner fa-spin"></i> Searching...</p>
      </td></tr>`;

      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      if (data.error) {
        throw new Error(data.error);
      }

      renderPunishmentsTable(type, data.punishments || [], false);
    } catch (error) {
      console.error('Search error:', error);
      const tbody = document.getElementById('punishments-tbody');
      tbody.innerHTML = `<tr><td colspan="${currentUser ? 7 : 6}" style="text-align: center;">
        <p class="error"><i class="fas fa-exclamation-triangle"></i> Search failed: ${error.message}</p>
      </td></tr>`;
    }
  }

  function renderPunishmentsTable(type, punishments, showLoading) {
    const tbody = document.getElementById('punishments-tbody');

    if (!punishments || punishments.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="${currentUser ? 7 : 6}" style="text-align: center;">
            <p class="no-results"><i class="fas fa-search"></i> No ${type} found.</p>
          </td>
        </tr>`;
    } else {
      tbody.innerHTML = punishments.map(p => {
        const playerName = p.player_name || 'Unknown';
        const rule = p.rule || 'Unknown';
        const staffName = p.staff_name || 'Unknown';
        const date = p.date ? new Date(p.date).toLocaleString() : 'Unknown';
        const duration = p.duration === "0" ? "Permanent" : (p.duration || 'Unknown');
        const evidenceLink = p.evidence_link || null;

        let evidenceCell = '';
        if (evidenceLink) {
          evidenceCell = `<td><a href="${escapeHtml(evidenceLink)}" target="_blank" class="evidence-link">
            <i class="fas fa-external-link-alt"></i> View Evidence
          </a></td>`;
        } else {
          evidenceCell = '<td><span class="no-evidence">No evidence</span></td>';
        }

        let actionsCell = '';
        if (currentUser) {
          actionsCell = `
            <td>
              <button class="btn btn-small btn-outline evidence-btn" data-id="${p.id}">
                <i class="fas fa-link"></i> ${evidenceLink ? 'Edit' : 'Add'} Evidence
              </button>
            </td>`;
        }

        return `
          <tr data-id="${p.id}">
            <td>${escapeHtml(playerName)}</td>
            <td>${escapeHtml(rule)}</td>
            <td>${escapeHtml(staffName)}</td>
            <td>${escapeHtml(date)}</td>
            <td>${escapeHtml(duration)}</td>
            ${evidenceCell}
            ${actionsCell}
          </tr>`;
      }).join('');

      // Attach event listeners for evidence buttons
      if (currentUser) {
        document.querySelectorAll('.evidence-btn').forEach(button => {
          button.addEventListener('click', function() {
            const punishmentId = this.dataset.id;
            showEvidenceModal(punishmentId);
          });
        });
      }
    }
  }

  // --- Evidence Modal ---
  function showEvidenceModal(punishmentId) {
    // Create modal HTML
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.id = 'evidence-modal';
    modal.innerHTML = `
      <div class="modal-content">
        <div class="modal-header">
          <h3><i class="fas fa-link"></i> Add Evidence Link</h3>
          <span class="modal-close">&times;</span>
        </div>
        <div class="modal-body">
          <form id="evidence-form">
            <div class="form-group">
              <label for="evidence-link"><i class="fas fa-link"></i> Evidence Link (Google Drive, YouTube, etc.)</label>
              <input type="url" id="evidence-link" name="evidence_link" required
                     placeholder="https://drive.google.com/... or https://youtube.com/...">
            </div>
            <div class="form-actions">
              <button type="button" class="btn btn-outline modal-close-btn">Cancel</button>
              <button type="submit" class="btn btn-primary">
                <i class="fas fa-save"></i> Save Evidence
              </button>
            </div>
          </form>
        </div>
      </div>
    `;

    document.body.appendChild(modal);

    // Attach event listeners
    const closeModal = () => {
      document.body.removeChild(modal);
    };

    modal.querySelector('.modal-close').addEventListener('click', closeModal);
    modal.querySelector('.modal-close-btn').addEventListener('click', closeModal);

    modal.querySelector('#evidence-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const evidenceLink = document.getElementById('evidence-link').value;

      try {
        const response = await fetch(`/api/punishments/${punishmentId}/evidence`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ evidence_link: evidenceLink })
        });

        if (response.ok) {
          showMessage('Evidence link saved successfully', 'success');
          closeModal();
          // Refresh current page
          const hash = window.location.hash || '#/';
          const page = hash.substring(2) || 'home';
          loadPageContent(page);
        } else {
          const data = await response.json();
          showMessage(data.error || 'Failed to save evidence link', 'error');
        }
      } catch (error) {
        showMessage('Failed to save evidence link: ' + error.message, 'error');
      }
    });

    // Close modal when clicking outside
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        closeModal();
      }
    });
  }

  // --- Utility Functions ---
  function escapeHtml(unsafe = "") {
    if (unsafe === null || unsafe === undefined) {
      return "";
    }
    return String(unsafe)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function showMessage(message, type) {
    // Create notification element
    const notification = document.createElement('div');
    notification.className = `notification notification-${type}`;
    notification.innerHTML = `
      <i class="fas ${type === 'success' ? 'fa-check-circle' : 'fa-exclamation-circle'}"></i>
      ${escapeHtml(message)}
    `;

    // Add to body
    document.body.appendChild(notification);

    // Remove after delay
    setTimeout(() => {
      if (notification.parentNode) {
        notification.parentNode.removeChild(notification);
      }
    }, 5000);
  }

  // --- Event Listeners ---
  window.addEventListener('hashchange', navigate);
  navigate(); // initial load
});