document.addEventListener('DOMContentLoaded', () => {
  console.log('DOM loaded, initializing app...');
  const mainContent = document.getElementById('main-content');
  const API_URL = '/api/punishments';
  let refreshInterval;
  let currentUser = null;
  let chatRefreshInterval = null;

  function safeFeatherReplace() {
    try {
      if (typeof feather !== 'undefined') {
        feather.replace();
      }
    } catch (error) {
      console.error('Feather replace failed:', error);
    }
  }

  const baseNav = document.querySelector('nav ul');
  if (baseNav) {
    baseNav.innerHTML = `
      <li>
        <a href="#/" class="nav-link active flex items-center px-4 py-2 rounded-lg transition-all sidebar-nav" data-page="home">
          <i data-feather="home" class="mr-2 w-5 h-5"></i>
          <span>Home</span>
        </a>
      </li>
      <li>
        <a href="#/warns" class="nav-link flex items-center px-4 py-2 rounded-lg transition-all sidebar-nav" data-page="warns">
          <i data-feather="alert-triangle" class="mr-2 w-5 h-5"></i>
          <span>Warns</span>
        </a>
      </li>
      <li>
        <a href="#/mutes" class="nav-link flex items-center px-4 py-2 rounded-lg transition-all sidebar-nav" data-page="mutes">
          <i data-feather="volume-x" class="mr-2 w-5 h-5"></i>
          <span>Mutes</span>
        </a>
      </li>
      <li>
        <a href="#/bans" class="nav-link flex items-center px-4 py-2 rounded-lg transition-all sidebar-nav" data-page="bans">
          <i data-feather="x" class="mr-2 w-5 h-5"></i>
          <span>Bans</span>
        </a>
      </li>
    `;
    safeFeatherReplace();
  }

  document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', function(e) {
      e.preventDefault();
      document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
      this.classList.add('active');
      const page = this.dataset.page || 'home';
      window.location.hash = `/${page}`;
    });
  });

  checkAuthStatus();

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
      } else {
        currentUser = null;
        updateNavbarForAuth(false);
      }
    } catch (error) {
      console.error('Error checking auth status:', error);
      currentUser = null;
      updateNavbarForAuth(false);
    }
  }

  function updateNavbarForAuth(isLoggedIn) {
    const nav = document.querySelector('nav ul');
    if (!nav) return;

    const existingAuthLinks = nav.querySelectorAll('[data-auth]');
    existingAuthLinks.forEach(link => link.remove());

    if (isLoggedIn) {
      const chatLink = document.createElement('li');
      chatLink.innerHTML = `
        <a href="#/staff-chat" class="nav-link flex items-center px-4 py-2 rounded-lg transition-all sidebar-nav" data-page="staff-chat" data-auth="true">
          <i data-feather="message-circle" class="mr-2 w-5 h-5"></i>
          <span>Staff Chat</span>
        </a>
      `;
      nav.appendChild(chatLink);

      const teamLink = document.createElement('li');
      teamLink.innerHTML = `
        <a href="#/team-management" class="nav-link flex items-center px-4 py-2 rounded-lg transition-all sidebar-nav" data-page="team-management" data-auth="true">
          <i data-feather="users" class="mr-2 w-5 h-5"></i>
          <span>Team Management</span>
        </a>
      `;
      nav.appendChild(teamLink);

      const logoutLink = document.createElement('li');
      logoutLink.innerHTML = `
        <a href="#" class="nav-link flex items-center px-4 py-2 rounded-lg transition-all sidebar-nav" data-auth="true">
          <i data-feather="log-out" class="mr-2 w-5 h-5"></i>
          <span>Logout</span>
        </a>
      `;
      logoutLink.querySelector('a').onclick = (e) => {
        e.preventDefault();
        logout();
      };
      nav.appendChild(logoutLink);
    } else {
      const loginLink = document.createElement('li');
      loginLink.innerHTML = `
        <a href="#/login" class="nav-link flex items-center px-4 py-2 rounded-lg transition-all sidebar-nav" data-page="login" data-auth="true">
          <i data-feather="log-in" class="mr-2 w-5 h-5"></i>
          <span>Login</span>
        </a>
      `;
      nav.appendChild(loginLink);
    }

    document.querySelectorAll('.nav-link').forEach(link => {
      link.removeEventListener('click', handleNavClick);
      link.addEventListener('click', handleNavClick);
    });

    safeFeatherReplace();
  }

  function handleNavClick(e) {
    e.preventDefault();
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    this.classList.add('active');
    const page = this.dataset.page || 'home';
    window.location.hash = `/${page}`;
  }

  async function logout() {
    try {
      const response = await fetch('/api/auth/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      if (response.ok) {
        currentUser = null;
        updateNavbarForAuth(false);
        window.location.hash = '#/';
        showMessage('Logged out successfully', 'success');
      } else {
        throw new Error('Logout failed');
      }
    } catch (error) {
      console.error('Logout error:', error);
      showMessage('Logout failed: ' + error.message, 'error');
    }
  }

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

    document.querySelectorAll('.nav-link').forEach(link => {
      link.classList.toggle('active', link.dataset.page === page);
    });

    loadPageContent(page);

    if (['warns', 'mutes', 'bans'].includes(page)) {
      refreshInterval = setInterval(() => loadPageContent(page), 60000);
    } else if (page === 'staff-chat') {
      chatRefreshInterval = setInterval(() => loadStaffChat(), 10000);
    }
  }

  function loadPageContent(page) {
    mainContent.innerHTML = `
      <div class="page-content max-w-6xl mx-auto min-h-screen flex items-center justify-center">
        <div class="loading text-center">
          <i data-feather="loader" class="mx-auto text-4xl animate-spin mb-4 text-primary-500"></i>
          <p class="text-gray-600">Loading ${page}...</p>
        </div>
      </div>
    `;
    safeFeatherReplace();

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
        if (!currentUser) {
          window.location.hash = '#/login';
          return;
        }
        loadStaffChatPage();
        break;
      case 'team-management':
        if (!currentUser) {
          window.location.hash = '#/login';
          return;
        }
        loadTeamManagementPage();
        break;
      default:
        mainContent.innerHTML = `
          <div class="page-content max-w-6xl mx-auto text-center py-12">
            <i data-feather="alert-triangle" class="mx-auto text-6xl text-red-500 mb-4"></i>
            <h2 class="text-2xl font-bold text-gray-900 mb-2">Page Not Found</h2>
            <p class="text-gray-600">The page "${page}" doesn't exist.</p>
          </div>
        `;
        safeFeatherReplace();
    }
  }

  function loadHomePage() {
    mainContent.innerHTML = `
      <div class="page-content max-w-6xl mx-auto" data-aos="fade-up">
        <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center"><i data-feather="home" class="mr-3 w-8 h-8"></i> Welcome to the Punishment Directory</h2>
        <div class="welcome-text text-gray-600 mb-8 text-lg leading-relaxed" data-aos="fade-up" data-aos-delay="100">
          <p class="mb-4">This directory provides a public log of all punishments issued on our server. We believe in transparency and accountability for all moderation actions.</p>
          <p class="mb-4">Use the navigation links above to view specific types of punishments. The lists are updated automatically to ensure accuracy and completeness.</p>
          <div class="mt-8 p-6 bg-blue-50 rounded-lg border border-blue-100" data-aos="fade-up" data-aos-delay="200">
            <h3 class="text-lg font-semibold text-blue-800 mb-2 flex items-center"><i data-feather="info" class="mr-2 w-5 h-5"></i> Recent Activity</h3>
            <p id="recent-activity" class="text-blue-700">Loading recent activity...</p>
          </div>
        </div>
        <div class="stats-summary" data-aos="fade-up" data-aos-delay="300">
          <h3 class="text-2xl font-semibold text-gray-900 mb-6 flex items-center"><i data-feather="bar-chart-2" class="mr-3 w-8 h-8"></i> Quick Stats</h3>
          <div id="quick-stats" class="stats-grid grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6">
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover">
              <div class="stat-value text-3xl font-bold text-gray-900 mb-2">Loading...</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Total Punishments</div>
            </div>
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover">
              <div class="stat-value text-3xl font-bold text-yellow-600 mb-2">Loading...</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Warnings</div>
            </div>
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover">
              <div class="stat-value text-3xl font-bold text-blue-600 mb-2">Loading...</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Mutes</div>
            </div>
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover">
              <div class="stat-value text-3xl font-bold text-red-600 mb-2">Loading...</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Bans</div>
            </div>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();
    loadQuickStats();
  }

  async function loadQuickStats() {
    try {
      const response = await fetch('/api/punishments/stats');
      if (response.ok) {
        const data = await response.json();
        const statsContainer = document.getElementById('quick-stats');
        const recentActivity = document.getElementById('recent-activity');
        if (statsContainer) {
          statsContainer.innerHTML = `
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover">
              <div class="stat-value text-3xl font-bold text-gray-900 mb-2">${data.totalPunishments || 0}</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Total Punishments</div>
            </div>
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover">
              <div class="stat-value text-3xl font-bold text-yellow-600 mb-2">${data.totalWarns || 0}</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Warnings</div>
            </div>
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover">
              <div class="stat-value text-3xl font-bold text-blue-600 mb-2">${data.totalMutes || 0}</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Mutes</div>
            </div>
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover">
              <div class="stat-value text-3xl font-bold text-red-600 mb-2">${data.totalBans || 0}</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Bans</div>
            </div>
          `;
        }
        if (recentActivity) {
          recentActivity.textContent = `${data.recentPunishments || 0} punishments in the last 24 hours`;
        }
      } else {
        throw new Error(`HTTP ${response.status}`);
      }
    } catch (error) {
      console.error('Error loading stats:', error);
      const statsContainer = document.getElementById('quick-stats');
      if (statsContainer) {
        statsContainer.innerHTML = `
          <div class="col-span-full text-center py-8">
            <p class="error text-red-500 mb-2">Failed to load statistics: ${error.message}</p>
            <p class="text-gray-500">Please check your connection or try again later.</p>
          </div>
        `;
      }
      document.getElementById('recent-activity').textContent = 'Unable to load recent activity';
    }
    safeFeatherReplace();
  }

  function loadLoginPage() {
    if (currentUser) {
      window.location.hash = '#/';
      return;
    }

    mainContent.innerHTML = `
      <div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 px-4">
        <div class="w-full max-w-md">
          <div class="text-center mb-10">
            <div class="flex justify-center mb-6">
              <div class="bg-primary-600 p-4 rounded-2xl shadow-lg floating-card">
                <i data-feather="shield" class="text-white w-12 h-12"></i>
              </div>
            </div>
            <h2 class="text-3xl font-bold text-white mb-2 flex items-center justify-center"><i data-feather="log-in" class="mr-2 w-8 h-8"></i> Staff Login</h2>
            <p class="text-gray-300">Access the moderation dashboard</p>
          </div>
          <div class="bg-white rounded-2xl shadow-xl p-8 card-hover">
            <form id="login-form" class="space-y-6">
              <div class="form-group">
                <label for="username" class="block text-sm font-medium text-gray-700 mb-2 flex items-center"><i data-feather="user" class="mr-2 h-4 w-4"></i> Username</label>
                <input type="text" id="username" name="username" required class="block w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
              </div>
              <div class="form-group">
                <label for="password" class="block text-sm font-medium text-gray-700 mb-2 flex items-center"><i data-feather="lock" class="mr-2 h-4 w-4"></i> Password</label>
                <input type="password" id="password" name="password" required class="block w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
              </div>
              <button type="submit" class="w-full flex justify-center items-center py-3 px-4 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 transition duration-300">
                <i data-feather="log-in" class="mr-2 h-5 w-5"></i>
                Sign in
              </button>
              <div id="login-message" class="form-message"></div>
            </form>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();

    document.getElementById('login-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('username').value;
      const password = document.getElementById('password').value;
      const messageEl = document.getElementById('login-message');

      try {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password })
        });

        const data = await response.json();
        if (response.ok && data.success) {
          currentUser = data.user;
          updateNavbarForAuth(true);
          window.location.hash = '#/';
          showMessage('Login successful', 'success');
        } else {
          messageEl.innerHTML = `<p class="error flex items-center justify-center"><i data-feather="alert-circle" class="mr-2"></i> ${data.error || 'Login failed'}</p>`;
          safeFeatherReplace();
        }
      } catch (error) {
        messageEl.innerHTML = `<p class="error flex items-center justify-center"><i data-feather="alert-circle" class="mr-2"></i> Login failed: ${error.message}</p>`;
        safeFeatherReplace();
      }
    });
  }

  function loadStaffChatPage() {
    if (!currentUser) {
      window.location.hash = '#/login';
      return;
    }

    mainContent.innerHTML = `
      <div class="page-content max-w-4xl mx-auto" data-aos="fade-up">
        <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center"><i data-feather="message-circle" class="mr-3 w-8 h-8"></i> Staff Chat</h2>
        <div class="chat-container bg-white rounded-xl shadow-lg border border-gray-100">
          <div id="chat-messages" class="chat-messages">
            <p class="loading flex items-center justify-center text-gray-500"><i data-feather="loader" class="mr-2 animate-spin"></i> Loading messages...</p>
          </div>
          <div class="chat-input-container">
            <form id="chat-form" class="flex gap-4">
              <input type="text" id="chat-message" placeholder="Type your message..." required class="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
              <button type="submit" class="btn btn-primary">
                <i data-feather="send" class="mr-2"></i> Send
              </button>
            </form>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();

    loadStaffChat();

    document.getElementById('chat-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const messageInput = document.getElementById('chat-message');
      const message = messageInput.value.trim();

      if (message) {
        try {
          const response = await fetch('/api/staff/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message })
          });

          if (response.ok) {
            messageInput.value = '';
            loadStaffChat();
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
                  <span class="message-user font-semibold text-gray-900">${escapeHtml(msg.staff_name)}</span>
                  <span class="message-time text-gray-500">${new Date(msg.timestamp).toLocaleString()}</span>
                </div>
                <div class="message-content bg-gray-100 p-4 rounded-lg border-l-4 border-black">${escapeHtml(msg.message)}</div>
              </div>
            `).join('');
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
          } else {
            messagesContainer.innerHTML = '<p class="no-messages flex items-center justify-center"><i data-feather="message-circle" class="mr-2"></i> No messages yet. Be the first to send one!</p>';
            safeFeatherReplace();
          }
        }
      }
    } catch (error) {
      console.error('Error loading chat:', error);
      document.getElementById('chat-messages').innerHTML = '<p class="error flex items-center justify-center"><i data-feather="alert-triangle" class="mr-2"></i> Failed to load chat messages.</p>';
      safeFeatherReplace();
    }
    safeFeatherReplace();
  }

  function loadTeamManagementPage() {
    if (!currentUser) {
      window.location.hash = '#/login';
      return;
    }

    mainContent.innerHTML = `
      <div class="page-content max-w-6xl mx-auto" data-aos="fade-up">
        <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center"><i data-feather="users" class="mr-3 w-8 h-8"></i> Team Management</h2>
        <div class="team-management-container space-y-8">
          <div class="add-staff-section bg-gray-50 p-6 rounded-xl border border-gray-200">
            <h3 class="text-xl font-semibold text-gray-900 mb-4 flex items-center"><i data-feather="user-plus" class="mr-3 w-5 h-5"></i> Add New Staff Member</h3>
            <form id="add-staff-form" class="space-y-4">
              <div class="form-row grid grid-cols-1 md:grid-cols-3 gap-4">
                <div class="form-group">
                  <label for="new-username" class="block text-sm font-medium text-gray-700 mb-2 flex items-center"><i data-feather="user" class="mr-2 h-4 w-4"></i> Username</label>
                  <input type="text" id="new-username" name="username" required class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
                </div>
                <div class="form-group">
                  <label for="new-password" class="block text-sm font-medium text-gray-700 mb-2 flex items-center"><i data-feather="lock" class="mr-2 h-4 w-4"></i> Password</label>
                  <input type="password" id="new-password" name="password" required class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
                </div>
                <div class="form-group">
                  <label for="new-role" class="block text-sm font-medium text-gray-700 mb-2 flex items-center"><i data-feather="tag" class="mr-2 h-4 w-4"></i> Role</label>
                  <select id="new-role" name="role" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
                    <option value="staff">Staff</option>
                    <option value="admin">Admin</option>
                    <option value="owner">Owner</option>
                  </select>
                </div>
              </div>
              <button type="submit" class="btn btn-primary">
                <i data-feather="user-plus" class="mr-2 w-4 h-4"></i> Add Staff Member
              </button>
              <div id="add-staff-message" class="form-message"></div>
            </form>
          </div>
          <div class="staff-list-section bg-white p-6 rounded-xl border border-gray-100">
            <h3 class="text-xl font-semibold text-gray-900 mb-4 flex items-center"><i data-feather="users" class="mr-3 w-5 h-5"></i> Current Staff Members</h3>
            <div id="staff-list-container">
              <p class="loading flex items-center justify-center text-gray-500"><i data-feather="loader" class="mr-2 animate-spin"></i> Loading staff members...</p>
            </div>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();

    loadStaffList();

    document.getElementById('add-staff-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('new-username').value;
      const password = document.getElementById('new-password').value;
      const role = document.getElementById('new-role').value;
      const messageEl = document.getElementById('add-staff-message');

      try {
        const response = await fetch('/api/staff/users', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password, role })
        });

        const data = await response.json();
        if (response.ok && data.success) {
          messageEl.innerHTML = `<p class="success flex items-center"><i data-feather="check-circle" class="mr-2"></i> ${data.message}</p>`;
          document.getElementById('add-staff-form').reset();
          loadStaffList();
          showMessage('Staff member added successfully', 'success');
        } else {
          messageEl.innerHTML = `<p class="error flex items-center"><i data-feather="alert-circle" class="mr-2"></i> ${data.error || 'Failed to add staff member'}</p>`;
        }
        safeFeatherReplace();
      } catch (error) {
        messageEl.innerHTML = `<p class="error flex items-center"><i data-feather="alert-circle" class="mr-2"></i> Failed to add staff member: ${error.message}</p>`;
        safeFeatherReplace();
      }
    });
  }

  async function loadStaffList() {
    try {
      const response = await fetch('/api/staff/users');
      if (response.ok) {
        const data = await response.json();
        const container = document.getElementById('staff-list-container');

        if (data.users && data.users.length > 0) {
          container.innerHTML = data.users.map(user => `
            <div class="staff-member-card flex justify-between items-center p-4 border border-gray-200 rounded-lg bg-gray-50 hover:bg-gray-100">
              <div class="staff-member-info flex-1">
                <div class="staff-member-name font-semibold text-black mb-1 flex items-center"><i data-feather="user" class="mr-2 h-4 w-4"></i> ${escapeHtml(user.username)}</div>
                <div class="staff-member-role text-gray-500 text-sm mb-1 flex items-center"><i data-feather="tag" class="mr-2 h-4 w-4"></i> ${escapeHtml(user.role)}</div>
                <div class="staff-member-uuid text-gray-500 text-xs flex items-center"><i data-feather="hash" class="mr-2 h-4 w-4"></i> ${user.uuid ? escapeHtml(user.uuid) : 'N/A'}</div>
              </div>
              ${user.username !== currentUser.username ? `
                <div class="staff-member-actions">
                  <button class="btn btn-small delete-staff-btn bg-red-600 hover:bg-red-700 text-white px-3 py-1 rounded flex items-center" data-username="${user.username}">
                    <i data-feather="trash-2" class="mr-1 h-4 w-4"></i> Delete
                  </button>
                </div>
              ` : `
                <div class="staff-member-actions">
                  <span class="current-user text-gray-500 italic text-sm">(You)</span>
                </div>
              `}
            </div>
          `).join('');

          document.querySelectorAll('.delete-staff-btn').forEach(button => {
            button.addEventListener('click', function() {
              const username = this.dataset.username;
              deleteStaffMember(username);
            });
          });
        } else {
          container.innerHTML = '<p class="no-results flex items-center justify-center"><i data-feather="users" class="mr-2"></i> No staff members found.</p>';
        }
        safeFeatherReplace();
      }
    } catch (error) {
      console.error('Error loading staff list:', error);
      document.getElementById('staff-list-container').innerHTML = '<p class="error flex items-center justify-center"><i data-feather="alert-triangle" class="mr-2"></i> Failed to load staff members.</p>';
      safeFeatherReplace();
    }
  }

  async function deleteStaffMember(username) {
    if (!confirm(`Are you sure you want to delete staff member "${username}"?`)) return;

    try {
      const response = await fetch(`/api/staff/users/${username}`, { method: 'DELETE' });
      const data = await response.json();
      if (response.ok && data.success) {
        showMessage('Staff member deleted successfully', 'success');
        loadStaffList();
      } else {
        showMessage(data.error || 'Failed to delete staff member', 'error');
      }
    } catch (error) {
      showMessage('Failed to delete staff member: ' + error.message, 'error');
    }
  }

  async function loadPunishmentsPage(type) {
    try {
      let url = `${API_URL}/${type}`;
      renderPunishmentsPage(type, [], true);

      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data = await response.json();
      if (data.error) {
        throw new Error(data.error);
      }

      renderPunishmentsTable(type, data.punishments || [], false);
    } catch (error) {
      console.error(`Error fetching ${type}:`, error);
      mainContent.innerHTML = `
        <div class="page-content max-w-6xl mx-auto">
          <p class="error flex items-center justify-center"><i data-feather="alert-triangle" class="mr-2"></i> Could not load ${type}. Please try again later.</p>
          <p class="error-details text-sm text-gray-500 mt-2 text-center">Error: ${error.message}</p>
        </div>
      `;
      safeFeatherReplace();
    }
  }

  function renderPunishmentsPage(type, punishments, showLoading) {
    const typeTitle = type.charAt(0).toUpperCase() + type.slice(1);

    mainContent.innerHTML = `
      <div class="page-content max-w-6xl mx-auto" data-aos="fade-up">
        <div class="flex flex-col md:flex-row justify-between items-start md:items-center mb-8">
          <div>
            <h2 class="text-3xl font-bold text-gray-900 mb-2 flex items-center"><i data-feather="gavel" class="mr-3 w-8 h-8"></i> All ${escapeHtml(typeTitle)}</h2>
            <p class="text-gray-600">All issued ${type} on our server</p>
          </div>
          <div class="mt-4 md:mt-0">
            <button class="bg-primary-600 hover:bg-primary-700 text-white px-5 py-2.5 rounded-lg flex items-center transition" onclick="exportData('${type}')">
              <i data-feather="download" class="mr-2 w-4 h-4"></i> Export Data
            </button>
          </div>
        </div>

        <div class="search-container bg-gray-50 p-6 rounded-xl border border-gray-200 mb-8">
          <div class="search-box grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
            <div class="form-group">
              <label class="block text-sm font-medium text-gray-700 mb-1">Player Name</label>
              <input type="text" id="search-player" placeholder="Search by player..." class="search-input w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
            </div>
            <div class="form-group">
              <label class="block text-sm font-medium text-gray-700 mb-1">Rule Violated</label>
              <input type="text" id="search-rule" placeholder="Search by rule..." class="search-input w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
            </div>
            <div class="flex space-x-2">
              <button id="search-button" class="flex-1 bg-primary-600 hover:bg-primary-700 text-white px-4 py-2.5 rounded-lg flex items-center justify-center transition">
                <i data-feather="search" class="mr-2 w-4 h-4"></i> Search
              </button>
              <button id="clear-search" class="flex-1 bg-gray-500 hover:bg-gray-600 text-white px-4 py-2.5 rounded-lg flex items-center justify-center transition">
                <i data-feather="x" class="mr-2 w-4 h-4"></i> Clear
              </button>
            </div>
          </div>
        </div>

        <div class="table-container bg-white rounded-xl shadow-lg overflow-hidden border border-gray-100">
          <div class="overflow-x-auto">
            <table class="punishments-table min-w-full divide-y divide-gray-200">
              <thead class="bg-black text-white">
                <tr>
                  <th class="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider">Player</th>
                  <th class="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider">Rule</th>
                  <th class="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider">Staff</th>
                  <th class="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider">Date</th>
                  <th class="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider">Duration</th>
                  <th class="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider">Evidence</th>
                  ${currentUser ? '<th class="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider">Actions</th>' : ''}
                </tr>
              </thead>
              <tbody id="punishments-tbody" class="bg-white divide-y divide-gray-200">
                ${showLoading ?
                  `<tr><td colspan="${currentUser ? 7 : 6}" class="px-6 py-4 text-center">
                    <p class="loading flex items-center justify-center text-gray-500"><i data-feather="loader" class="mr-2 animate-spin"></i> Loading...</p>
                  </td></tr>` :
                  ''
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();

    document.getElementById('search-button').addEventListener('click', () => {
      performSearch(type);
    });

    document.getElementById('clear-search').addEventListener('click', () => {
      document.getElementById('search-player').value = '';
      document.getElementById('search-rule').value = '';
      performSearch(type);
    });

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
      let url = `${API_URL}/${type}?`;
      const params = [];

      if (playerFilter) params.push(`player=${encodeURIComponent(playerFilter)}`);
      if (ruleFilter) params.push(`rule=${encodeURIComponent(ruleFilter)}`);

      url += params.join('&');

      const tbody = document.getElementById('punishments-tbody');
      tbody.innerHTML = `<tr><td colspan="${currentUser ? 7 : 6}" class="px-6 py-4 text-center">
        <p class="loading flex items-center justify-center text-gray-500"><i data-feather="loader" class="mr-2 animate-spin"></i> Searching...</p>
      </td></tr>`;
      safeFeatherReplace();

      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data = await response.json();
      if (data.error) {
        throw new Error(data.error);
      }

      renderPunishmentsTable(type, data.punishments || [], false);
    } catch (error) {
      console.error('Search error:', error);
      const tbody = document.getElementById('punishments-tbody');
      tbody.innerHTML = `<tr><td colspan="${currentUser ? 7 : 6}" class="px-6 py-4 text-center">
        <p class="error flex items-center justify-center"><i data-feather="alert-triangle" class="mr-2"></i> Search failed: ${error.message}</p>
      </td></tr>`;
      safeFeatherReplace();
    }
  }

  function renderPunishmentsTable(type, punishments, showLoading) {
    const tbody = document.getElementById('punishments-tbody');

    if (showLoading) {
      tbody.innerHTML = `<tr><td colspan="${currentUser ? 7 : 6}" class="px-6 py-4 text-center">
        <p class="loading flex items-center justify-center text-gray-500"><i data-feather="loader" class="mr-2 animate-spin"></i> Loading...</p>
      </td></tr>`;
      safeFeatherReplace();
      return;
    }

    if (!punishments || punishments.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="${currentUser ? 7 : 6}" class="px-6 py-4 text-center">
            <p class="no-results flex items-center justify-center"><i data-feather="search" class="mr-2"></i> No ${type} found.</p>
          </td>
        </tr>`;
      safeFeatherReplace();
      return;
    }

    // Filter hidden punishments for public view
    const filteredPunishments = punishments.filter(p => !(p.hidden && !currentUser));

    if (filteredPunishments.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="${currentUser ? 7 : 6}" class="px-6 py-4 text-center">
            <p class="no-results flex items-center justify-center"><i data-feather="search" class="mr-2"></i> No visible ${type} found.</p>
          </td>
        </tr>`;
      safeFeatherReplace();
      return;
    }

    tbody.innerHTML = filteredPunishments.map(p => {
      const playerName = p.player_name || 'Unknown';
      const rule = p.rule || 'Unknown';
      const staffName = p.staff_name || 'Unknown';
      const date = p.date ? new Date(p.date).toLocaleString() : 'Unknown';
      const duration = p.duration === "0" ? "Permanent" : (p.duration || 'Unknown');
      const evidenceLink = p.evidence_link || null;
      const isHidden = p.hidden || false;

      let evidenceCell = '';
      if (evidenceLink) {
        evidenceCell = `<td class="px-6 py-4 whitespace-nowrap">
          <a href="${escapeHtml(evidenceLink)}" target="_blank" class="evidence-link flex items-center">
            <i data-feather="external-link" class="mr-1 w-4 h-4"></i> View Evidence
          </a>
        </td>`;
      } else {
        evidenceCell = '<td class="px-6 py-4 whitespace-nowrap"><span class="no-evidence text-gray-500 italic text-sm">No evidence</span></td>';
      }

      let actionsCell = '';
      if (currentUser) {
        actionsCell = `
          <td class="px-6 py-4 whitespace-nowrap">
            <div class="action-buttons space-y-2">
              <button class="btn btn-small btn-outline evidence-btn w-full flex justify-center" data-id="${p.id}">
                <i data-feather="link" class="mr-1 h-4 w-4"></i> ${evidenceLink ? 'Edit' : 'Add'} Evidence
              </button>
              <button class="btn btn-small ${isHidden ? 'btn-primary' : 'btn-outline'} hide-btn w-full flex justify-center" data-id="${p.id}" data-hidden="${isHidden}">
                <i data-feather="${isHidden ? 'eye' : 'eye-off'}" class="mr-1 h-4 w-4"></i> ${isHidden ? 'Unhide' : 'Hide'}
              </button>
            </div>
          </td>`;
      }

      return `
        <tr data-id="${p.id}" class="table-row-hover ${isHidden ? 'hidden-punishment' : ''}">
          <td class="px-6 py-4 whitespace-nowrap">
            <div class="flex items-center">
              <div class="flex-shrink-0 h-10 w-10 bg-gray-200 rounded-full flex items-center justify-center mr-4">
                <span class="text-gray-700 font-medium">${playerName.charAt(0)}</span>
              </div>
              <div>${escapeHtml(playerName)} ${isHidden ? '<span class="hidden-badge">HIDDEN</span>' : ''}</div>
            </div>
          </td>
          <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">${escapeHtml(rule)}</td>
          <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">${escapeHtml(staffName)}</td>
          <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">${escapeHtml(date)}</td>
          <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">${escapeHtml(duration)}</td>
          ${evidenceCell}
          ${actionsCell}
        </tr>`;
    }).join('');

    if (currentUser) {
      document.querySelectorAll('.evidence-btn').forEach(button => {
        button.addEventListener('click', function() {
          const punishmentId = this.dataset.id;
          showEvidenceModal(punishmentId);
        });
      });

      document.querySelectorAll('.hide-btn').forEach(button => {
        button.addEventListener('click', async function() {
          const punishmentId = this.dataset.id;
          const isHidden = this.dataset.hidden === 'true';
          showHidePunishmentModal(punishmentId, isHidden);
        });
      });
    }
    safeFeatherReplace();
  }

  function showEvidenceModal(punishmentId) {
    const modal = document.createElement('div');
    modal.className = 'modal fixed inset-0 z-50 flex items-center justify-center p-4 bg-black bg-opacity-50';
    modal.id = 'evidence-modal';
    modal.innerHTML = `
      <div class="modal-content bg-white rounded-xl w-full max-w-md mx-auto">
        <div class="modal-header flex justify-between items-center p-6 border-b border-gray-200 bg-gray-50 rounded-t-xl">
          <h3 class="text-lg font-semibold text-gray-900 flex items-center"><i data-feather="link" class="mr-2"></i> Add Evidence Link</h3>
          <span class="modal-close text-2xl font-bold cursor-pointer text-gray-500 hover:text-black">&times;</span>
        </div>
        <div class="modal-body p-6">
          <form id="evidence-form" class="space-y-4">
            <div class="form-group">
              <label for="evidence-link" class="block text-sm font-medium text-gray-700 mb-2 flex items-center"><i data-feather="link" class="mr-2 h-4 w-4"></i> Evidence Link (Google Drive, YouTube, etc.)</label>
              <input type="url" id="evidence-link" name="evidence_link" required placeholder="https://drive.google.com/... or https://youtube.com/..." class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
            </div>
            <div class="form-actions flex gap-3 justify-end">
              <button type="button" class="btn btn-outline px-4 py-2">Cancel</button>
              <button type="submit" class="btn btn-primary px-4 py-2">
                <i data-feather="save" class="mr-2 h-4 w-4"></i> Save Evidence
              </button>
            </div>
          </form>
        </div>
      </div>
    `;

    document.body.appendChild(modal);
    safeFeatherReplace();

    const closeModal = () => {
      document.body.removeChild(modal);
    };

    modal.querySelector('.modal-close').addEventListener('click', closeModal);
    modal.querySelector('.btn-outline').addEventListener('click', closeModal);

    modal.querySelector('#evidence-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const evidenceLink = document.getElementById('evidence-link').value;

      try {
        const response = await fetch(`/api/punishments/${punishmentId}/evidence`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ evidence_link: evidenceLink })
        });

        if (response.ok) {
          showMessage('Evidence link saved successfully', 'success');
          closeModal();
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

    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        closeModal();
      }
    });
  }

  function showHidePunishmentModal(punishmentId, currentHiddenStatus) {
    const modal = document.createElement('div');
    modal.className = 'modal fixed inset-0 z-50 flex items-center justify-center p-4 bg-black bg-opacity-50';
    modal.id = 'hide-punishment-modal';
    modal.innerHTML = `
      <div class="modal-content bg-white rounded-xl w-full max-w-md mx-auto">
        <div class="modal-header flex justify-between items-center p-6 border-b border-gray-200 bg-gray-50 rounded-t-xl">
          <h3 class="text-lg font-semibold text-gray-900 flex items-center"><i data-feather="${currentHiddenStatus ? 'eye' : 'eye-off'}" class="mr-2"></i> ${currentHiddenStatus ? 'Unhide' : 'Hide'} Punishment</h3>
          <span class="modal-close text-2xl font-bold cursor-pointer text-gray-500 hover:text-black">&times;</span>
        </div>
        <div class="modal-body p-6">
          <p class="mb-4">Are you sure you want to ${currentHiddenStatus ? 'unhide' : 'hide'} this punishment?</p>
          <p class="text-sm text-gray-600 mb-6"><strong>Note:</strong> ${currentHiddenStatus ? 'Unhiding' : 'Hiding'} this punishment will ${currentHiddenStatus ? 'make it visible' : 'remove it from'} the public directory.</p>
          <div class="form-actions flex gap-3 justify-end">
            <button type="button" class="btn btn-outline px-4 py-2">Cancel</button>
            <button type="button" class="btn btn-primary px-4 py-2" id="confirm-hide-btn">
              <i data-feather="${currentHiddenStatus ? 'eye' : 'eye-off'}" class="mr-2 h-4 w-4"></i> ${currentHiddenStatus ? 'Unhide' : 'Hide'} Punishment
            </button>
          </div>
        </div>
      </div>
    `;

    document.body.appendChild(modal);
    safeFeatherReplace();

    const closeModal = () => {
      document.body.removeChild(modal);
    };

    modal.querySelector('.modal-close').addEventListener('click', closeModal);
    modal.querySelector('.btn-outline').addEventListener('click', closeModal);

    modal.querySelector('#confirm-hide-btn').addEventListener('click', async () => {
      try {
        const response = await fetch(`/api/punishments/${punishmentId}/hide`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ hidden: !currentHiddenStatus })
        });

        if (response.ok) {
          showMessage(`Punishment ${currentHiddenStatus ? 'unhidden' : 'hidden'} successfully`, 'success');
          closeModal();
          const hash = window.location.hash || '#/';
          const page = hash.substring(2) || 'home';
          loadPageContent(page); // Re-fetch and re-render to apply filter
        } else {
          const data = await response.json();
          showMessage(data.error || 'Failed to update punishment visibility', 'error');
        }
      } catch (error) {
        showMessage('Failed to update punishment visibility: ' + error.message, 'error');
      }
    });

    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        closeModal();
      }
    });
  }

  function exportData(type) {
    showMessage(`Exporting ${type} data... (Implement CSV/JSON export logic here)`, 'success');
  }

  function escapeHtml(unsafe = '') {
    if (unsafe === null || unsafe === undefined) return '';
    return String(unsafe)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function showMessage(message, type) {
    const notification = document.createElement('div');
    notification.className = `notification fixed top-4 right-4 z-50 p-4 rounded-lg shadow-lg flex items-center gap-3 min-w-[300px] notification-enter ${
      type === 'success' ? 'bg-green-100 text-green-700 border border-green-200' : 'bg-red-100 text-red-700 border border-red-200'
    }`;
    notification.innerHTML = `
      <i data-feather="${type === 'success' ? 'check-circle' : 'alert-circle'}" class="w-5 h-5"></i>
      ${escapeHtml(message)}
    `;

    document.body.appendChild(notification);
    safeFeatherReplace();

    setTimeout(() => {
      if (notification.parentNode) {
        notification.parentNode.removeChild(notification);
      }
    }, 5000);
  }

  window.addEventListener('hashchange', navigate);
  safeFeatherReplace();
  navigate();
});
