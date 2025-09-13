document.addEventListener('DOMContentLoaded', () => {
  const mainContent = document.getElementById('main-content');
  const navLinks = document.querySelectorAll('.nav-link');
  const API_URL = '/api/punishments';
  let refreshInterval;

  // --- Router Logic ---
  function navigate() {
    if (refreshInterval) {
      clearInterval(refreshInterval);
      refreshInterval = null;
    }

    const hash = window.location.hash || '#/';
    const page = hash.substring(2) || 'home';

    navLinks.forEach(link => {
      link.classList.toggle('active', link.dataset.page === page);
    });

    loadPageContent(page);

    if (['warns', 'mutes', 'bans'].includes(page)) {
      refreshInterval = setInterval(() => loadPageContent(page), 60000);
    }
  }

  // --- Page Content Loading ---
  function loadPageContent(page) {
    mainContent.innerHTML = `
      <div class="page-content">
        <p class="loading">Loading...</p>
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
      default:
        mainContent.innerHTML = `
          <div class="page-content">
            <p class="error">Page not found.</p>
          </div>`;
    }
  }

  function loadHomePage() {
    mainContent.innerHTML = `
      <div class="page-content">
        <h2>Welcome to the Punishment Directory</h2>
        <div class="welcome-text">
          <p>This directory provides a public log of all punishments issued on our server. We believe in transparency and accountability for all moderation actions.</p>
          <p>Use the navigation links above to view specific types of punishments. The lists are updated automatically.</p>
        </div>
      </div>`;
  }

  async function loadPunishmentsPage(type) {
    try {
      const response = await fetch(`${API_URL}/${type}`);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      if (data.error) {
        throw new Error(data.error);
      }

      renderPunishmentsTable(type, data.punishments || []);
    } catch (error) {
      console.error(`Error fetching ${type}:`, error);
      mainContent.innerHTML = `
        <div class="page-content">
          <p class="error">Could not load ${type}. Please try again later.</p>
          <p class="error-details">Error: ${error.message}</p>
        </div>`;
    }
  }

  // --- Rendering Logic ---
  function renderPunishmentsTable(type, punishments) {
    const typeTitle = type.charAt(0).toUpperCase() + type.slice(1);
    let tableHTML = `
      <div class="page-content">
        <h2>All ${escapeHtml(typeTitle)}</h2>
        <div class="table-container">
          <table class="punishments-table">
            <thead>
              <tr>
                <th>Player</th>
                <th>Rule</th>
                <th>Staff</th>
                <th>Date</th>
                <th>Duration</th>
              </tr>
            </thead>
            <tbody>`;

    if (!punishments || punishments.length === 0) {
      tableHTML += `
        <tr>
          <td colspan="5" style="text-align: center;">No ${type} found.</td>
        </tr>`;
    } else {
      punishments.forEach(p => {
        const playerName = p.playerName || 'Unknown';
        const rule = p.rule || 'Unknown';
        const staffName = p.staffName || 'Unknown';
        const date = p.date ? new Date(p.date).toLocaleString() : 'Unknown';
        const duration = p.duration === "0" ? "Permanent" : (p.duration || 'Unknown');

        tableHTML += `
          <tr>
            <td>${escapeHtml(playerName)}</td>
            <td>${escapeHtml(rule)}</td>
            <td>${escapeHtml(staffName)}</td>
            <td>${escapeHtml(date)}</td>
            <td>${escapeHtml(duration)}</td>
          </tr>`;
      });
    }

    tableHTML += `
            </tbody>
          </table>
        </div>
      </div>`;

    mainContent.innerHTML = tableHTML;
  }

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

  // --- Event Listeners ---
  window.addEventListener('hashchange', navigate);
  navigate(); // initial load
});