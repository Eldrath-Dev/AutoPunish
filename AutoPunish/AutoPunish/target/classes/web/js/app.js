// State management
let state = {
    token: localStorage.getItem('token'),
    username: localStorage.getItem('username'),
    activeTab: 'approvals'
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

// API base URL
const API_URL = '/api';

// Initialize application
function init() {
    // Check if user is logged in
    if (state.token && state.username) {
        showDashboard();
        loadData();
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
}

// Show login form
function showLoginForm() {
    loginForm.classList.remove('hidden');
    dashboard.classList.add('hidden');
    userInfo.classList.add('hidden');
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
}

// Load data based on active tab
function loadData() {
    if (state.activeTab === 'approvals') {
        loadApprovals();
    } else if (state.activeTab === 'punishments') {
        loadPunishments();
    }
}

// Load pending approvals
async function loadApprovals() {
    try {
        const response = await fetch(`${API_URL}/approvals`, {
            headers: {
                'Authorization': `Bearer ${state.token}`
            }
        });

        if (response.ok) {
            const approvals = await response.json();
            renderApprovals(approvals);
        } else {
            approvalsList.innerHTML = '<p class="error">Failed to load approvals</p>';
        }
    } catch (error) {
        console.error('Error loading approvals:', error);
        approvalsList.innerHTML = '<p class="error">Network error. Please try again.</p>';
    }
}

// Render approvals list
function renderApprovals(approvals) {
    if (approvals.length === 0) {
        approvalsList.innerHTML = '<p>No pending approvals!</p>';
        return;
    }

    let html = '<div class="approvals-grid">';

    approvals.forEach(approval => {
        const formattedDate = new Date(approval.queuedDate).toLocaleString();
        html += `
            <div class="approval-card">
                <h3>Approval ID: ${approval.approvalId}</h3>
                <p><strong>Player:</strong> ${approval.playerName}</p>
                <p><strong>Rule:</strong> ${approval.rule}</p>
                <p><strong>Punishment:</strong> ${approval.type} (${approval.duration === '0' ? 'Permanent' : approval.duration})</p>
                <p><strong>Requested by:</strong> ${approval.staffName}</p>
                <p><strong>Date:</strong> ${formattedDate}</p>
                <div class="approval-actions">
                    <button class="approve-btn" data-id="${approval.approvalId}">Approve</button>
                    <button class="deny-btn" data-id="${approval.approvalId}">Deny</button>
                </div>
            </div>
        `;
    });

    html += '</div>';
    approvalsList.innerHTML = html;

    // Add event listeners to buttons
    document.querySelectorAll('.approve-btn').forEach(btn => {
        btn.addEventListener('click', () => handleApproval(btn.getAttribute('data-id'), true));
    });

    document.querySelectorAll('.deny-btn').forEach(btn => {
        btn.addEventListener('click', () => handleApproval(btn.getAttribute('data-id'), false));
    });
}

// Handle approval or denial
async function handleApproval(id, isApproved) {
    // Sanitize the ID to ensure it's just the approval ID
    const sanitizedId = id.split('/').pop(); // Get the last part if it's a full path

    const action = isApproved ? 'approve' : 'deny';
    const button = event.target;
    const originalText = button.textContent;

    // Disable button and show loading state
    button.disabled = true;
    button.textContent = 'Processing...';

    try {
        const response = await fetch(`${API_URL}/approvals/${sanitizedId}/${action}?adminName=${state.username}`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${state.token}`
            }
        });

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
            const errorText = await response.text();
            console.error(`Failed to ${action} punishment:`, response.status, errorText);
            alert(`Failed to ${action} punishment. Server returned status: ${response.status}`);
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
    try {
        const response = await fetch(`${API_URL}/punishments?page=1&size=20`, {
            headers: {
                'Authorization': `Bearer ${state.token}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            renderPunishments(data.punishments);
        } else {
            punishmentsList.innerHTML = '<p class="error">Failed to load punishments</p>';
        }
    } catch (error) {
        console.error('Error loading punishments:', error);
        punishmentsList.innerHTML = '<p class="error">Network error. Please try again.</p>';
    }
}

// Render punishments list
function renderPunishments(punishments) {
    if (punishments.length === 0) {
        punishmentsList.innerHTML = '<p>No punishments found!</p>';
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
}

// Initialize the application
document.addEventListener('DOMContentLoaded', init);