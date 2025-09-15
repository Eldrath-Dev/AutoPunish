document.addEventListener('DOMContentLoaded', () => {
  console.log('DOM loaded, initializing app...'); // Debug log
  const mainContent = document.getElementById('main-content');
  const API_URL = '/api/punishments'; // Use this for real API; placeholders below for demo
  let refreshInterval;
  let currentUser = null;
  let chatRefreshInterval = null;

  // Helper: Safe Feather replace (prevents crashes if library fails)
  function safeFeatherReplace() {
    try {
      if (typeof feather !== 'undefined') {
        feather.replace();
        console.log('Feather icons updated'); // Debug
      } else {
        console.warn('Feather Icons not available');
      }
    } catch (error) {
      console.error('Feather replace failed:', error);
    }
  }

  // Initialize base nav IMMEDIATELY (before any async calls)
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
    safeFeatherReplace(); // Initial icons for base nav
    console.log('Base nav and icons initialized'); // Debug
  }

  // Attach click listeners to base nav links immediately
  document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', function(e) {
      e.preventDefault(); // Prevent default hash change
      document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
      this.classList.add('active');
      const page = this.dataset.page || 'home';
      window.location.hash = `/${page}`;
    });
  });

  // Now check auth (async, won't block nav)
  checkAuthStatus();

  // --- Authentication Functions ---
  async function checkAuthStatus() {
    try {
      console.log('Checking auth status...'); // Debug
      const response = await fetch('/api/auth/session').catch(() => null); // Graceful fail if no API
      if (response && response.ok) {
        const data = await response.json();
        if (data.authenticated) {
          currentUser = data.user || { username: 'DemoUser' }; // Fallback for demo
          updateNavbarForAuth(true);
          console.log('User authenticated:', currentUser.username); // Debug
        } else {
          currentUser = null;
          updateNavbarForAuth(false);
        }
      } else {
        // No API or not authenticated - treat as guest
        currentUser = null;
        updateNavbarForAuth(false);
        console.log('No auth API or guest mode'); // Debug
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

    // Remove ONLY existing auth-related links (base nav has no data-auth attr)
    const existingAuthLinks = nav.querySelectorAll('[data-auth]');
    existingAuthLinks.forEach(link => link.remove());

    if (isLoggedIn) {
      // Add Staff Chat, Team Management, and Logout
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

      console.log('Auth nav updated for logged-in user'); // Debug
    } else {
      // Add Login link
      const loginLink = document.createElement('li');
      loginLink.innerHTML = `
        <a href="#/login" class="nav-link flex items-center px-4 py-2 rounded-lg transition-all sidebar-nav" data-page="login" data-auth="true">
          <i data-feather="log-in" class="mr-2 w-5 h-5"></i>
          <span>Login</span>
        </a>
      `;
      nav.appendChild(loginLink);
      console.log('Auth nav updated for guest'); // Debug
    }

    // Reattach click listeners to ALL nav links (including new ones)
    document.querySelectorAll('.nav-link').forEach(link => {
      // Remove existing listener to avoid duplicates
      link.removeEventListener('click', handleNavClick);
      link.addEventListener('click', handleNavClick);
    });

    safeFeatherReplace(); // Update icons after adding links
  }

  // Unified nav click handler
  function handleNavClick(e) {
    e.preventDefault();
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    this.classList.add('active');
    const page = this.dataset.page || 'home';
    window.location.hash = `/${page}`;
  }

  async function logout() {
    try {
      // Simulate logout (replace with real API if needed)
      await new Promise(resolve => setTimeout(resolve, 500)); // Demo delay
      currentUser = null;
      updateNavbarForAuth(false);
      window.location.hash = '#/';
      showMessage('Logged out successfully', 'success');
      console.log('Logout completed'); // Debug
    } catch (error) {
      console.error('Logout error:', error);
      showMessage('Logout failed', 'error');
    }
  }

  // --- Router Logic ---
  function navigate() {
    console.log('Navigating...'); // Debug
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
    console.log('Current page:', page); // Debug

    // Update active class based on current page
    document.querySelectorAll('.nav-link').forEach(link => {
      link.classList.toggle('active', link.dataset.page === page);
    });

    loadPageContent(page);

    if (['warns', 'mutes', 'bans'].includes(page)) {
      refreshInterval = setInterval(() => loadPageContent(page), 60000); // Refresh every minute
    } else if (page === 'staff-chat') {
      chatRefreshInterval = setInterval(() => loadStaffChat(), 10000); // Refresh every 10s
    }
  }

  // --- Page Content Loading ---
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

  // --- Home Page (with placeholder stats for demo) ---
  function loadHomePage() {
    mainContent.innerHTML = `
      <div class="page-content max-w-6xl mx-auto" data-aos="fade-up">
        <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center"><i data-feather="home" class="mr-3 w-8 h-8"></i> Welcome to the Punishment Directory</h2>
        <div class="welcome-text text-gray-600 mb-8 text-lg leading-relaxed" data-aos="fade-up" data-aos-delay="100">
          <p class="mb-4">This directory provides a public log of all punishments issued on our server. We believe in transparency and accountability for all moderation actions.</p>
          <p class="mb-4">Use the navigation links above to view specific types of punishments. The lists are updated automatically to ensure accuracy and completeness.</p>
          <div class="mt-8 p-6 bg-blue-50 rounded-lg border border-blue-100" data-aos="fade-up" data-aos-delay="200">
            <h3 class="text-lg font-semibold text-blue-800 mb-2 flex items-center"><i data-feather="info" class="mr-2 w-5 h-5"></i> Recent Activity</h3>
            <p class="text-blue-700">24 punishments issued in the last 24 hours</p>
          </div>
        </div>
        <div class="stats-summary" data-aos="fade-up" data-aos-delay="300">
          <h3 class="text-2xl font-semibold text-gray-900 mb-6 flex items-center"><i data-feather="bar-chart-2" class="mr-3 w-8 h-8"></i> Quick Stats</h3>
          <div class="stats-grid grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6">
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover animate-fade-in">
              <div class="stat-value text-3xl font-bold text-gray-900 mb-2">1,248</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Total Punishments</div>
            </div>
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover animate-fade-in" style="animation-delay: 0.1s;">
              <div class="stat-value text-3xl font-bold text-yellow-600 mb-2">582</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Warnings (30 days)</div>
            </div>
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover animate-fade-in" style="animation-delay: 0.2s;">
              <div class="stat-value text-3xl font-bold text-blue-600 mb-2">312</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Active Mutes</div>
            </div>
            <div class="stat-card bg-white rounded-xl shadow-lg p-6 text-center border border-gray-100 card-hover animate-fade-in" style="animation-delay: 0.3s;">
              <div class="stat-value text-3xl font-bold text-red-600 mb-2">86</div>
              <div class="stat-label text-sm text-gray-500 uppercase tracking-wide">Permanent Bans</div>
            </div>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();
    // loadQuickStats(); // Uncomment if API available
  }

  // Placeholder for real stats API
  async function loadQuickStats() {
    // Demo data - replace with fetch('/api/punishments/stats')
    const demoData = {
      totalPunishments: 1248,
      totalWarns: 582,
      totalMutes: 312,
      totalBans: 86,
      recentPunishments: 24
    };
    // Update DOM with data (already handled in loadHomePage for demo)
    console.log('Stats loaded (demo):', demoData); // Debug
  }

  // --- Login Page ---
  function loadLoginPage() {
    if (currentUser) {
      window.location.hash = '#/';
      return;
    }

    mainContent.innerHTML = `
      <div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 px-4" data-aos="fade">
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
                <label for="username" class="block text-sm font-medium text-gray-700 mb-2 flex items-center"><i data-feather="user" class="mr-2 h-4 w-4 text-gray-400"></i> Username</label>
                <input type="text" id="username" name="username" required class="block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition" placeholder="Enter your username">
              </div>
              <div class="form-group">
                <label for="password" class="block text-sm font-medium text-gray-700 mb-2 flex items-center"><i data-feather="lock" class="mr-2 h-4 w-4 text-gray-400"></i> Password</label>
                <div class="relative">
                  <input type="password" id="password" name="password" required class="block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500 transition" placeholder="Enter your password">
                </div>
              </div>
              <div class="flex items-center justify-between">
                <div class="flex items-center">
                  <input id="remember-me" name="remember-me" type="checkbox" class="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded">
                  <label for="remember-me" class="ml-2 block text-sm text-gray-700">Remember me</label>
                </div>
                <div class="text-sm">
                  <a href="#" class="font-medium text-primary-600 hover:text-primary-500">Forgot password?</a>
                </div>
              </div>
              <button type="submit" class="w-full flex justify-center items-center py-3 px-4 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 transition duration-300">
                <i data-feather="log-in" class="mr-2 h-5 w-5"></i>
                Sign in
              </button>
              <div id="login-message" class="form-message"></div>
            </form>
            <div class="mt-6 text-center">
              <p class="text-sm text-gray-600">
                Don't have access? 
                <a href="#" class="font-medium text-primary-600 hover:text-primary-500">Contact an administrator</a>
              </p>
            </div>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();

    // Demo login handler (replace with real API)
    document.getElementById('login-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('username').value.trim();
      const password = document.getElementById('password').value;
      const messageEl = document.getElementById('login-message');

      if (!username || !password) {
        messageEl.innerHTML = `<p class="error flex items-center justify-center p-3 bg-red-100 text-red-700 rounded"><i data-feather="alert-circle" class="mr-2"></i> Please fill in all fields</p>`;
        safeFeatherReplace();
        return;
      }

      // Simulate API delay
      const submitBtn = e.target.querySelector('button[type="submit"]');
      const originalText = submitBtn.innerHTML;
      submitBtn.innerHTML = '<i data-feather="loader" class="mr-2 h-5 w-5 animate-spin"></i> Signing in...';
      submitBtn.disabled = true;
      safeFeatherReplace();

      setTimeout(() => {
        submitBtn.innerHTML = originalText;
        submitBtn.disabled = false;
        safeFeatherReplace();

        // Demo: Any non-empty login succeeds
        if (username && password) {
          currentUser = { username };
          updateNavbarForAuth(true);
          window.location.hash = '#/';
          showMessage('Login successful! Redirecting...', 'success');
        } else {
          messageEl.innerHTML = `<p class="error flex items-center justify-center p-3 bg-red-100 text-red-700 rounded"><i data-feather="alert-circle" class="mr-2"></i> Invalid credentials. Please try again.</p>`;
          safeFeatherReplace();
        }
      }, 1500);
    });
  }

  // --- Staff Chat Page (Placeholder data for demo) ---
  function loadStaffChatPage() {
    if (!currentUser) {
      window.location.hash = '#/login';
      return;
    }

    mainContent.innerHTML = `
      <div class="page-content max-w-4xl mx-auto" data-aos="fade-up">
        <h2 class="text-3xl font-bold text-gray-900 mb-8 flex items-center"><i data-feather="message-circle" class="mr-3 w-8 h-8"></i> Staff Chat</h2>
        <div class="chat-container bg-white rounded-xl shadow-lg border border-gray-100 h-96 md:h-[600px]">
          <div id="chat-messages" class="chat-messages p-6 overflow-y-auto">
            <p class="no-messages flex items-center justify-center text-gray-500 h-full"><i data-feather="message-circle" class="mr-2 w-6 h-6"></i> Loading messages...</p>
          </div>
          <div class="chat-input-container p-6 bg-gray-50 border-t border-gray-200">
            <form id="chat-form" class="flex gap-4">
              <input type="text" id="chat-message" placeholder="Type your message... (${currentUser.username})" required class="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500">
              <button type="submit" class="btn btn-primary px-6">
                <i data-feather="send" class="w-4 h-4"></i>
              </button>
            </form>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();

    loadStaffChat(); // Load initial messages

    document.getElementById('chat-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const messageInput = document.getElementById('chat-message');
      const message = messageInput.value.trim();

      if (message) {
        // Add message to chat (demo - replace with API POST)
        addChatMessage(currentUser.username, message);
        messageInput.value = '';
        // Simulate API send
        setTimeout(() => showMessage('Message sent!', 'success'), 300);
      }
    });
  }

  // Placeholder chat data and functions
  let chatMessages = [
    { staff_name: 'Admin1', message: 'Welcome to staff chat!', timestamp: new Date(Date.now() - 3600000).toISOString() },
    { staff_name: 'Moderator2', message: 'Checking recent bans.', timestamp: new Date().toISOString() }
  ];

  function loadStaffChat() {
    const messagesContainer = document.getElementById('chat-messages');
    if (!messagesContainer || !currentUser) return;

    if (chatMessages.length > 0) {
      messagesContainer.innerHTML = chatMessages.map(msg => `
        <div class="chat-message mb-4 pb-4 border-b border-gray-200 last:border-b-0 last:mb-0">
          <div class="message-header flex justify-between items-center mb-2 text-sm">
            <span class="message-user font-semibold text-gray-900">${escapeHtml(msg.staff_name)}</span>
            <span class="message-time text-gray-500">${new Date(msg.timestamp).toLocaleString()}</span>
          </div>
          <div class="message-content bg-gray-100 p-3 rounded-lg border-l-4 border-primary-500">${escapeHtml(msg.message)}</div>
        </div>
      `).join('');
      messagesContainer.scrollTop = messagesContainer.scrollHeight;
    } else {
      messagesContainer.innerHTML = '<p class="no-messages flex items-center justify-center text-gray-500 h-full"><i data-feather="message-circle" class="mr-2 w-6 h-6"></i> No messages yet. Be the first!</p>';
    }
    safeFeatherReplace();
  }

  function addChatMessage(user, message) {
    chatMessages.push({
      staff_name: user,
      message,
      timestamp: new Date().toISOString()
    });
    loadStaffChat();
  }

  // --- Team Management Page (Placeholder data for demo) ---
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
            <div id="staff-list-container" class="space-y-4">
              <p class="loading flex items-center justify-center text-gray-500 py-8"><i data-feather="loader" class="mr-2 w-6 h-6 animate-spin"></i> Loading staff members...</p>
            </div>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();

    loadStaffList();

    // Demo add staff handler (replace with real API)
    document.getElementById('add-staff-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('new-username').value.trim();
      const password = document.getElementById('new-password').value;
      const role = document.getElementById('new-role').value;
      const messageEl = document.getElementById('add-staff-message');

      if (!username || !password) {
        messageEl.innerHTML = `<p class="error p-3 bg-red-100 text-red-700 rounded flex items-center"><i data-feather="alert-circle" class="mr-2"></i> Please fill username and password</p>`;
        safeFeatherReplace();
        return;
      }

      // Simulate API
      setTimeout(() => {
        staffMembers.push({ username, role, uuid: 'demo-' + Date.now() }); // Add to demo data
        messageEl.innerHTML = `<p class="success p-3 bg-green-100 text-green-700 rounded flex items-center"><i data-feather="check-circle" class="mr-2"></i> Staff member "${username}" added successfully!</p>`;
        e.target.reset();
        loadStaffList();
        showMessage('Staff member added', 'success');
        safeFeatherReplace();
      }, 1000);
    });
  }

  // Placeholder staff data
  let staffMembers = [
    { username: 'Admin1', role: 'owner', uuid: 'uuid-1' },
    { username: 'Moderator2', role: 'admin', uuid: 'uuid-2' },
    { username: currentUser?.username || 'You', role: 'staff', uuid: 'uuid-3' }
  ];

  function loadStaffList() {
    const container = document.getElementById('staff-list-container');
    if (!container || !currentUser) return;

    if (staffMembers.length > 0) {
      container.innerHTML = staffMembers.map(user => `
        <div class="staff-member-card flex justify-between items-center p-4 border border-gray-200 rounded-lg bg-gray-50 hover:bg-gray-100 transition-all">
          <div class="staff-member-info flex-1">
            <div class="staff-member-name font-semibold text-black mb-1 flex items-center"><i data-feather="user" class="mr-2 h-4 w-4"></i> ${escapeHtml(user.username)}</div>
            <div class="staff-member-role text-gray-500 text-sm mb-1 flex items-center"><i data-feather="tag" class="mr-2 h-4 w-4"></i> ${escapeHtml(user.role)}</div>
            <div class="staff-member-uuid text-gray-500 text-xs flex items-center"><i data-feather="hash" class="mr-2 h-4 w-4"></i> ${user.uuid || 'N/A'}</div>
          </div>
          ${user.username !== currentUser.username ? `
            <div class="staff-member-actions">
              <button class="delete-staff-btn bg-red-600 hover:bg-red-700 text-white px-4 py-2 rounded-lg flex items-center text-sm transition" data-username="${user.username}">
                <i data-feather="trash-2" class="mr-2 h-4 w-4"></i> Delete
              </button>
            </div>
          ` : `
            <div class="staff-member-actions">
              <span class="current-user text-gray-500 italic text-sm">(You)</span>
            </div>
          `}
        </div>
      `).join('');

      // Attach delete listeners
      document.querySelectorAll('.delete-staff-btn').forEach(button => {
        button.addEventListener('click', (e) => {
          e.stopPropagation();
          const username = button.dataset.username;
          deleteStaffMember(username);
        });
      });
    } else {
      container.innerHTML = '<p class="no-results flex items-center justify-center py-8 text-gray-500"><i data-feather="users" class="mr-2 w-6 h-6"></i> No staff members found.</p>';
    }
    safeFeatherReplace();
  }

  async function deleteStaffMember(username) {
    if (!confirm(`Are you sure you want to delete staff member "${username}"?`)) return;

    // Simulate API delete
    setTimeout(() => {
      staffMembers = staffMembers.filter(user => user.username !== username);
      loadStaffList();
      showMessage('Staff member deleted successfully', 'success');
    }, 500);
  }

  // --- Punishments Pages (Placeholder data for demo) ---
  async function loadPunishmentsPage(type) {
    // Demo data - replace with fetch(`${API_URL}/${type}`)
    const demoPunishments = [
      {
        id: 1,
        player_name: 'John_Doe',
        rule: type === 'warns' ? 'Spamming' : type === 'mutes' ? 'Toxic Behavior' : 'Cheating',
        staff_name: 'Moderator1',
        date: new Date(Date.now() - 86400000).toISOString(), // Yesterday
        duration: type === 'bans' ? 'Permanent' : '24 hours',
        evidence_link: 'https://example.com/evidence1',
        hidden: false
      },
      {
        id: 2,
        player_name: 'Alice_Smith',
        rule: type === 'warns' ? 'Harassment' : type === 'mutes' ? 'Spamming' : 'Hacking',
        staff_name: 'Admin2',
        date: new Date().toISOString(),
        duration: type === 'bans' ? 'Permanent' : '7 days',
        evidence_link: null,
        hidden: type === 'bans' // Demo hidden for bans
      }
    ];

    renderPunishmentsPage(type, demoPunishments, false);
  }

  function renderPunishmentsPage(type, punishments, showLoading) {
    const typeTitle = type.charAt(0).toUpperCase() + type.slice(1).toLowerCase() + ' Records';

    mainContent.innerHTML = `
      <div class="page-content max-w-6xl mx-auto" data-aos="fade-up">
        <div class="flex flex-col md:flex-row justify-between items-start md:items-center mb-8">
          <div>
            <h2 class="text-3xl font-bold text-gray-900 mb-2 flex items-center"><i data-feather="gavel" class="mr-3 w-8 h-8"></i> ${typeTitle}</h2>
            <p class="text-gray-600">All issued ${type} on our server</p>
          </div>
          <div class="mt-4 md:mt-0">
            <button class="bg-primary-600 hover:bg-primary-700 text-white px-5 py-2.5 rounded-lg flex items-center transition" onclick="exportData('${type}')">
              <i data-feather="download" class="mr-2 w-4 h-4"></i> Export Data
            </button>
          </div>
        </div>

        <!-- Search Bar -->
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

        <!-- Table -->
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
                  `<tr><td colspan="${currentUser ? 7 : 6}" class="px-6 py-8 text-center">
                    <div class="loading flex flex-col items-center">
                      <i data-feather="loader" class="w-8 h-8 animate-spin mb-2 text-primary-500"></i>
                      <p class="text-gray-500">Loading ${type}...</p>
                    </div>
                  </td></tr>` : ''
                }
              </tbody>
            </table>
          </div>
          <div class="px-6 py-4 bg-gray-50 border-t border-gray-200">
            <div class="flex items-center justify-between text-sm text-gray-700">
              <div>Showing 1 to ${punishments.length} of ${punishments.length} results</div>
              <div class="flex space-x-2">
                <button class="px-3 py-1 rounded-md bg-gray-200 text-gray-700 hover:bg-gray-300">Previous</button>
                <button class="px-3 py-1 rounded-md bg-primary-600 text-white">1</button>
                <button class="px-3 py-1 rounded-md bg-gray-200 text-gray-700 hover:bg-gray-300">Next</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;
    safeFeatherReplace();

    // Render table with data
    renderPunishmentsTable(type, punishments, false);

    // Attach search listeners
    document.getElementById('search-button').addEventListener('click', () => performSearch(type));
    document.getElementById('clear-search').addEventListener('click', () => {
      document.getElementById('search-player').value = '';
      document.getElementById('search-rule').value = '';
      performSearch(type);
    });
    ['search-player', 'search-rule'].forEach(id => {
      document.getElementById(id).addEventListener('keypress', (e) => {
        if (e.key === 'Enter') performSearch(type);
      });
    });
  }

  // Demo search (filter local data)
  async function performSearch(type) {
    const playerFilter = document.getElementById('search-player').value.trim().toLowerCase();
    const ruleFilter = document.getElementById('search-rule').value.trim().toLowerCase();

    // Demo data (filter)
    let filtered = demoPunishments.filter(p => 
      (!playerFilter || p.player_name.toLowerCase().includes(playerFilter)) &&
      (!ruleFilter || p.rule.toLowerCase().includes(ruleFilter))
    );

    const tbody = document.getElementById('punishments-tbody');
    tbody.innerHTML = `<tr><td colspan="${currentUser ? 7 : 6}" class="px-6 py-8 text-center">
      <div class="loading flex flex-col items-center">
        <i data-feather="search" class="w-8 h-8 mb-2 text-primary-500"></i>
        <p class="text-gray-500">Searching...</p>
      </div>
    </td></tr>`;
    safeFeatherReplace();

    setTimeout(() => renderPunishmentsTable(type, filtered, false), 500); // Simulate delay
  }

  function renderPunishmentsTable(type, punishments, showLoading) {
    const tbody = document.getElementById('punishments-tbody');
    if (!tbody) return;

    if (showLoading || (!punishments || punishments.length === 0)) {
      tbody.innerHTML = `
        <tr>
          <td colspan="${currentUser ? 7 : 6}" class="px-6 py-8 text-center">
            <div class="no-results flex flex-col items-center">
              <i data-feather="search" class="w-8 h-8 mb-2 text-gray-400"></i>
              <p class="text-gray-500">No ${type} found matching your search.</p>
            </div>
          </td>
        </tr>
      `;
      safeFeatherReplace();
      return;
    }

    tbody.innerHTML = punishments.map(p => {
      const playerName = p.player_name || 'Unknown';
      const rule = p.rule || 'Unknown';
      const staffName = p.staff_name || 'Unknown';
      const date = p.date ? new Date(p.date).toLocaleString() : 'Unknown';
      const duration = p.duration || (type === 'bans' ? 'Permanent' : 'N/A');
      const evidenceLink = p.evidence_link;
      const isHidden = p.hidden || false;

      let evidenceCell = evidenceLink ? 
        `<td class="px-6 py-4 whitespace-nowrap">
          <a href="${escapeHtml(evidenceLink)}" target="_blank" class="evidence-link inline-flex items-center text-black hover:text-white px-3 py-1 rounded border border-gray-300 hover:bg-black transition">
            <i data-feather="external-link" class="mr-1 w-4 h-4"></i> View
          </a>
        </td>` :
        `<td class="px-6 py-4 whitespace-nowrap">
          <span class="no-evidence text-gray-500 italic text-sm">No evidence</span>
        </td>`;

      let actionsCell = '';
      if (currentUser) {
        actionsCell = `
          <td class="px-6 py-4 whitespace-nowrap">
            <div class="action-buttons space-y-2">
              <button class="evidence-btn w-full flex justify-center items-center px-3 py-1 border border-gray-300 text-sm rounded hover:bg-gray-100 transition" data-id="${p.id}">
                <i data-feather="link" class="mr-1 w-4 h-4"></i> ${evidenceLink ? 'Edit' : 'Add'} Evidence
              </button>
              <button class="hide-btn w-full flex justify-center items-center px-3 py-1 ${isHidden ? 'bg-green-600 text-white' : 'border border-gray-300 text-sm'} rounded hover:${isHidden ? 'bg-green-700' : 'bg-gray-100'} transition" data-id="${p.id}" data-hidden="${isHidden}">
                <i data-feather="${isHidden ? 'eye' : 'eye-off'}" class="mr-1 w-4 h-4"></i> ${isHidden ? 'Unhide' : 'Hide'}
              </button>
            </div>
          </td>
        `;
      }

      return `
        <tr data-id="${p.id}" class="table-row-hover ${isHidden ? 'hidden-punishment' : ''}">
          <td class="px-6 py-4">
            <div class="flex items-center">
              <div class="flex-shrink-0 h-10 w-10 bg-gray-200 rounded-full flex items-center justify-center mr-4">
                <span class="text-gray-700 font-medium text-sm">${playerName.charAt(0).toUpperCase()}</span>
              </div>
              <div class="font-medium text-gray-900">${escapeHtml(playerName)} ${isHidden ? '<span class="hidden-badge ml-2">HIDDEN</span>' : ''}</div>
            </div>
          </td>
          <td class="px-6 py-4 text-sm font-medium text-gray-900">${escapeHtml(rule)}</td>
          <td class="px-6 py-4 text-sm text-gray-500">${escapeHtml(staffName)}</td>
          <td class="px-6 py-4 text-sm text-gray-500">${escapeHtml(date)}</td>
          <td class="px-6 py-4 text-sm text-gray-500">${escapeHtml(duration)}</td>
          ${evidenceCell}
          ${actionsCell}
        </tr>
      `;
    }).join('');

    // Attach action listeners if logged in
    if (currentUser) {
      document.querySelectorAll('.evidence-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const id = btn.dataset.id;
          showEvidenceModal(id);
        });
      });

      document.querySelectorAll('.hide-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const id = btn.dataset.id;
          const hidden = btn.dataset.hidden === 'true';
          showHidePunishmentModal(id, hidden);
        });
      });
    }

    safeFeatherReplace();
  }

  // --- Modals ---
  function showEvidenceModal(punishmentId) {
    const modal = document.createElement('div');
    modal.className = 'modal fixed inset-0 z-50 flex items-center justify-center p-4 bg-black bg-opacity-50';
    modal.innerHTML = `
      <div class="modal-content bg-white rounded-xl w-full max-w-md">
        <div class="modal-header flex justify-between items-center p-6 border-b border-gray-200 bg-gray-50 rounded-t-xl">
          <h3 class="text-lg font-semibold text-gray-900 flex items-center"><i data-feather="link" class="mr-2 w-5 h-5"></i> Add/Edit Evidence</h3>
          <span class="modal-close text-2xl font-bold cursor-pointer text-gray-500 hover:text-black">&times;</span>
        </div>
        <div class="modal-body p-6">
          <form id="evidence-form" class="space-y-4">
            <div class="form-group">
              <label for="evidence-link" class="block text-sm font-medium text-gray-700 mb-2 flex items-center"><i data-feather="link" class="mr-2 h-4 w-4"></i> Evidence URL</label>
              <input type="url" id="evidence-link" placeholder="https://example.com/evidence" class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500" required>
            </div>
            <div class="form-actions flex gap-3 justify-end">
              <button type="button" class="btn btn-outline px-4 py-2">Cancel</button>
              <button type="submit" class="btn btn-primary px-4 py-2">Save</button>
            </div>
          </form>
        </div>
      </div>
    `;
    document.body.appendChild(modal);
    safeFeatherReplace();

    // Close handlers
    const closeModal = () => document.body.removeChild(modal);
    modal.querySelector('.modal-close').addEventListener('click', closeModal);
    modal.querySelector('.btn-outline').addEventListener('click', closeModal);
    modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

    // Submit handler (demo)
    modal.querySelector('#evidence-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const link = document.getElementById('evidence-link').value;
      // Simulate save
      setTimeout(() => {
        showMessage('Evidence saved!', 'success');
        closeModal();
        // Refresh page
        navigate();
      }, 500);
    });
  }

  function showHidePunishmentModal(punishmentId, isHidden) {
    const action = isHidden ? 'Unhide' : 'Hide';
    const modal = document.createElement('div');
    modal.className = 'modal fixed inset-0 z-50 flex items-center justify-center p-4 bg-black bg-opacity-50';
    modal.innerHTML = `
      <div class="modal-content bg-white rounded-xl w-full max-w-md">
        <div class="modal-header flex justify-between items-center p-6 border-b border-gray-200 bg-gray-50 rounded-t-xl">
          <h3 class="text-lg font-semibold text-gray-900 flex items-center"><i data-feather="${isHidden ? 'eye' : 'eye-off'}" class="mr-2 w-5 h-5"></i> ${action} Punishment</h3>
          <span class="modal-close text-2xl font-bold cursor-pointer text-gray-500 hover:text-black">&times;</span>
        </div>
        <div class="modal-body p-6">
          <p class="mb-4">Are you sure you want to ${action.toLowerCase()} this punishment (ID: ${punishmentId})?</p>
          <p class="text-sm text-gray-600 mb-6"><strong>Note:</strong> This will ${isHidden ? 'make it visible again' : 'hide it from public view'}.</p>
          <div class="form-actions flex gap-3 justify-end">
            <button type="button" class="btn btn-outline px-4 py-2">Cancel</button>
            <button type="button" class="btn btn-primary px-4 py-2" id="confirm-hide-btn">${action}</button>
          </div>
        </div>
      </div>
    `;
    document.body.appendChild(modal);
    safeFeatherReplace();

    // Close handlers
    const closeModal = () => document.body.removeChild(modal);
    modal.querySelector('.modal-close').addEventListener('click', closeModal);
    modal.querySelector('.btn-outline').addEventListener('click', closeModal);
    modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

    // Confirm handler (demo)
    modal.querySelector('#confirm-hide-btn').addEventListener('click', async () => {
      // Simulate toggle
      setTimeout(() => {
        showMessage(`Punishment ${action.toLowerCase()}d successfully`, 'success');
        closeModal();
        navigate(); // Refresh page
      }, 500);
    });
  }

  // Placeholder export
  function exportData(type) {
    showMessage(`Exporting ${type} data as CSV... (Download would start here)`, 'success');
    // Implement real export logic (e.g., generate CSV and download)
  }

  // --- Utility Functions ---
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
      <i data-feather="${type === 'success' ? 'check-circle' : 'alert-circle'}" class="w-5 h-5 flex-shrink-0"></i>
      <span>${escapeHtml(message)}</span>
    `;
    document.body.appendChild(notification);
    safeFeatherReplace();

    setTimeout(() => {
      if (notification.parentNode) {
        notification.classList.add('animate-fade-in'); // Fade out animation
        setTimeout(() => notification.remove(), 300);
      }
    }, 4000);
  }

  // --- Event Listeners ---
  window.addEventListener('hashchange', navigate);

  // Initial load
  safeFeatherReplace();
  navigate();
  console.log('App fully initialized'); // Debug
});