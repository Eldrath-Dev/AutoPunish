// State management
let state = {
    token: localStorage.getItem('token'),
    username: localStorage.getItem('username'),
    activeTab: 'approvals',
    refreshInterval: null // Variable to hold the interval ID
};

// DOM elements
const loginForm = document.getElementById('login-form');
const dashboard = document.getElementById('dashboard');
const userInfo = document.getElementById('user-info');
const usernameSpan = document.getElementById('username');
const logoutBtn = document.getElementById('logout-btn');
const loginError = document.getElementById('login-error');
const approvalsList = document.getElementById('approvals-list');
const punishmentsList = document.getElementById('punishments-list');
const tabButtons = document.querySelectorAll('.tab-btn');
const tabContents = document.querySelectorAll('.tab-content');
const approvalLoadingIndicator = approvalsList.querySelector('.loading-indicator');
const punishmentLoadingIndicator = punishmentsList.querySelector('.loading-indicator');
const playerResults = document.getElementById('player-results'); // Assuming you might have a player results div
const playerLoadingIndicator = playerResults.querySelector('.loading-indicator'); // Get the loading indicator for players

// API base URL
const API_URL = '/api';
const REFRESH_INTERVAL_MS = 60000; // 1 minute in milliseconds

// Initialize application
function init() {
    // Check if user is logged in
    if (state.token && state.username) {
        showDashboard();
        loadData();
        startAutoRefresh(); // Start auto-refresh when logged in
    } else {
        showLoginForm();
    }

    // Set up event listeners
    document.getElementById('login').addEventListener('submit', handleLogin);
    logoutBtn.addEventListener('click', handleLogout);

    // Tab switching
    tabButtons.forEach(button => {
        button.addEventListener('click', () => {
            const tabName = button.getAttribute('data-tab');
            switchTab(tabName);
        });
    });

    // Player search form submission
    document.getElementById('player-search').addEventListener('submit', handlePlayerSearch);
}

// Show login form
function showLoginForm() {
    loginForm.classList.remove('hidden');
    dashboard.classList.add('hidden');
    userInfo.classList.add('hidden');
    stopAutoRefresh(); // Ensure refresh is stopped when logging out
}

// Show dashboard
function showDashboard() {
    loginForm.classList.add('hidden');
    dashboard.classList.remove('hidden');
    userInfo.classList.remove('hidden');
    usernameSpan.textContent = state.username;
}

// Switch between tabs
function switchTab(tabName) {
    state.activeTab = tabName;

    // Update active button
    tabButtons.forEach(btn => {
        if (btn.getAttribute('data-tab') === tabName) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });

    // Show active content
    tabContents.forEach(content => {
        if (content.id === tabName) {
            content.classList.remove('hidden');
        } else {
            content.classList.add('hidden');
        }
    });

    // Load data for the active tab
    loadData();
}

// Handle login form submission
async function handleLogin(e) {
    e.preventDefault();

    const username = document.getElementById('username-input').value;
    const password = document.getElementById('password-input').value;

    try {
        const response = await fetch(`${API_URL}/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });

        const data = await response.json();

        if (response.ok && data.success) {
            // Store authentication info
            state.token = data.token;
            state.username = data.username;
            localStorage.setItem('token', data.token);
            localStorage.setItem('username', data.username);

            // Show dashboard
            showDashboard();
            loadData();
            startAutoRefresh(); // Start auto-refresh after successful login
        } else {
            // Show error
            loginError.textContent = data.error || 'Login failed';
            loginError.classList.remove('hidden');
        }
    } catch (error) {
        console.error('Login error:', error);
        loginError.textContent = 'Network error. Please try again.';
        loginError.classList.remove('hidden');
    }
}

// Handle logout
function handleLogout() {
    // Clear authentication info
    state.token = null;
    state.username = null;
    localStorage.removeItem('token');
    localStorage.removeItem('username');

    // Show login form
    showLoginForm();
    stopAutoRefresh(); // Stop auto-refresh on logout
}

// Start the auto-refresh interval
function startAutoRefresh() {
    // Clear any existing interval to prevent duplicates
    stopAutoRefresh();

    state.refreshInterval = setInterval(() => {
        console.log("Auto-refreshing data...");
        loadData(); // Reload data for the current tab
    }, REFRESH_INTERVAL_MS);
}

// Stop the auto-refresh interval
function stopAutoRefresh() {
    if (state.refreshInterval) {
        clearInterval(state.refreshInterval);
        state.refreshInterval = null;
        console.log("Auto-refresh stopped.");
    }
}

// Load data based on active tab
function loadData() {
    if (state.activeTab === 'approvals') {
        loadApprovals();
    } else if (state.activeTab === 'punishments') {
        loadPunishments();
    }
    // Add other tabs if needed, e.g., player search
}

// Helper to show loading indicators and hide no-data messages
function setLoadingState(listElement, hasData) {
    const loadingIndicator = listElement.querySelector('.loading-indicator');
    const noDataMessage = listElement.querySelector('.no-data');

    if (loadingIndicator) loadingIndicator.classList.add('hidden');
    if (noDataMessage) noDataMessage.classList.toggle('hidden', hasData);
}

// Load pending approvals
async function loadApprovals() {
    setLoadingState(approvalsList, false); // Show loading, hide no-data
    try {
        const response = await fetch(`${API_URL}/approvals`, {
            headers: {
                'Authorization': `Bearer ${state.token}`
            }
        });

        if (response.ok) {
            const approvals = await response.json();
            renderApprovals(approvals);
            setLoadingState(approvalsList, approvals.length > 0); // Update loading state based on data
        } else {
            approvalsList.innerHTML = '<p class="error">Failed to load approvals</p>';
            setLoadingState(approvalsList, false);
        }
    } catch (error) {
        console.error('Error loading approvals:', error);
        approvalsList.innerHTML = '<p class="error">Network error. Please try again.</p>';
        setLoadingState(approvalsList, false);
    }
}

// Render approvals list
function renderApprovals(approvals) {
    if (approvals.length === 0) {
        approvalsList.innerHTML = '<p class="no-data">No pending approvals!</p>';
        setLoadingState(approvalsList, false); // Show no-data, hide loading
        return;
    }

    let html = '<div class="approvals-grid">';

    approvals.forEach(approval => {
        const formattedDate = new Date(approval.queuedDate).toLocaleString();
        // Ensure approval.approvalId exists before using it
        const approvalId = approval.approvalId || 'N/A';

        html += `
            <div class="approval-card">
                <h3>Approval ID: ${approvalId}</h3>
                <p><strong>Player:</strong> ${approval.playerName}</p>
                <p><strong>Rule:</strong> ${approval.rule}</p>
                <p><strong>Punishment:</strong> ${approval.type} (${approval.duration === '0' ? 'Permanent' : approval.duration})</p>
                <p><strong>Requested by:</strong> ${approval.staffName}</p>
                <p><strong>Date:</strong> ${formattedDate}</p>
                <div class="approval-actions">
                    <button class="approve-btn" data-id="${approvalId}">Approve</button>
                    <button class="deny-btn" data-id="${approvalId}">Deny</button>
                </div>
            </div>
        `;
    });

    html += '</div>';
    approvalsList.innerHTML = html;
    setLoadingState(approvalsList, true); // Indicate data is loaded

    // Add event listeners to buttons
    document.querySelectorAll('.approve-btn').forEach(btn => {
        btn.addEventListener('click', (e) => handleApproval(e, btn.getAttribute('data-id'), true));
    });

    document.querySelectorAll('.deny-btn').forEach(btn => {
        btn.addEventListener('click', (e) => handleApproval(e, btn.getAttribute('data-id'), false));
    });
}

// Handle approval or denial
async function handleApproval(event, id, isApproved) {
    // Sanitize the ID to ensure it's just the approval ID
    const sanitizedId = id.toString().trim(); // Get the last part if it's a full path and trim whitespace

    const action = isApproved ? 'approve' : 'deny';
    const button = event.target;
    const originalText = button.textContent;

    // Disable button and show loading state
    button.disabled = true;
    button.textContent = 'Processing...';

    try {
        // Encode the approval ID and admin name to handle special characters
        const encodedId = encodeURIComponent(sanitizedId);
        const encodedAdminName = encodeURIComponent(state.username);

        const response = await fetch(`${API_URL}/approvals/${encodedId}/${action}?adminName=${encodedAdminName}`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${state.token}`,
                'Content-Type': 'application/json'
            }
        });

        // Check if response is OK before parsing JSON
        if (response.ok) {
            const result = await response.json();
            if (result.success) {
                // Reload approvals
                loadApprovals();
                alert(`Punishment ${action}d successfully!`);
            } else {
                alert(`Failed to ${action} punishment: ${result.error}`);
            }
        } else {
            // Try to get more detailed error from response body
            const errorText = await response.text(); // Use text() for potential non-JSON error messages
            console.error(`Failed to ${action} punishment:`, response.status, errorText);
            alert(`Failed to ${action} punishment. Server returned status: ${response.status}. Check console for details.`);
        }
    } catch (error) {
        console.error(`Error ${action}ing punishment:`, error);
        alert(`Network error while trying to ${action} punishment.`);
    } finally {
        // Re-enable button
        button.disabled = false;
        button.textContent = originalText;
    }
}

// Load punishments
async function loadPunishments() {
    setLoadingState(punishmentsList, false); // Show loading, hide no-data
    try {
        const response = await fetch(`${API_URL}/punishments?page=1&size=20`, {
            headers: {
                'Authorization': `Bearer ${state.token}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            renderPunishments(data.punishments);
            setLoadingState(punishmentsList, data.punishments.length > 0); // Update loading state based on data
        } else {
            punishmentsList.innerHTML = '<p class="error">Failed to load punishments</p>';
            setLoadingState(punishmentsList, false);
        }
    } catch (error) {
        console.error('Error loading punishments:', error);
        punishmentsList.innerHTML = '<p class="error">Network error. Please try again.</p>';
        setLoadingState(punishmentsList, false);
    }
}

// Render punishments list
function renderPunishments(punishments) {
    if (punishments.length === 0) {
        punishmentsList.innerHTML = '<p class="no-data">No punishments found!</p>';
        setLoadingState(punishmentsList, false); // Show no-data, hide loading
        return;
    }

    let html = '<table class="punishments-table">';
    html += `
        <thead>
            <tr>
                <th>Player</th>
                <th>Rule</th>
                <th>Punishment</th>
                <th>Staff</th>
                <th>Date</th>
            </tr>
        </thead>
        <tbody>
    `;

    punishments.forEach(punishment => {
        const formattedDate = new Date(punishment.date).toLocaleString();
        html += `
            <tr>
                <td>${punishment.playerName}</td>
                <td>${punishment.rule}</td>
                <td>${punishment.type} (${punishment.duration === '0' ? 'Permanent' : punishment.duration})</td>
                <td>${punishment.staffName}</td>
                <td>${formattedDate}</td>
            </tr>
        `;
    });

    html += '</tbody></table>';
    punishmentsList.innerHTML = html;
    setLoadingState(punishmentsList, true); // Indicate data is loaded
}

// Handle player search
async function handlePlayerSearch(e) {
    e.preventDefault();
    const playerName = document.getElementById('player-search-input').value;
    if (!playerName) return;

    // Clear previous results and show loading
    playerResults.innerHTML = '<div class="loading-indicator">Loading player info...</div>';
    playerResults.classList.remove('hidden');

    try {
        // Assuming you have an endpoint like /api/players/{name} or you can search online players
        // For now, let's simulate a search for online players
        const onlinePlayers = await fetch(`${API_URL}/players`, {
            headers: { 'Authorization': `Bearer ${state.token}` }
        });

        if (onlinePlayers.ok) {
            const data = await onlinePlayers.json();
            const foundPlayers = data.players.filter(p => p.name.toLowerCase().includes(playerName.toLowerCase()));

            renderPlayerSearch(foundPlayers);
        } else {
            playerResults.innerHTML = '<p class="error">Failed to load players</p>';
        }
    } catch (error) {
        console.error('Player search error:', error);
        playerResults.innerHTML = '<p class="error">Network error. Please try again.</p>';
    }
}

function renderPlayerSearch(players) {
    if (players.length === 0) {
        playerResults.innerHTML = '<p class="no-data">Player not found or not online.</p>';
        return;
    }

    let html = '<ul>';
    players.forEach(player => {
        html += `<li>${player.name} (UUID: ${player.uuid})</li>`; // Placeholder, you'd add more info here
    });
    html += '</ul>';
    playerResults.innerHTML = html;
}

// Initialize the application
document.addEventListener('DOMContentLoaded', init);