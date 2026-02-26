let chatOpen = false;
let sidebarOpen = true;
let conversations = [];
let activeConvId = null;
let contextMenuTarget = null;

const AI_RESPONSES = [
  "I can help you analyze your test suites! What would you like to know?",
  "Looking at your test structure, I'd suggest organizing related API tests into sub-suites for better maintainability.",
  "For endpoint tests with parent dependencies, make sure your `requires` field correctly maps response paths. Need an example?",
  "Prechecks are great for gating tests conditionally — I can help you set up complex multi-condition prechecks.",
  "Pattern match checks use regex — `^[0-9]+$` validates numeric strings. Want help crafting a pattern?",
  "Value match with `NOT EMPTY` is useful for asserting arrays returned from list endpoints have data.",
  "I noticed you can star a test case as a session validator. This is used to auto-authenticate before each run.",
  "Drag-and-drop reordering is supported for both test cases and folders. Execution follows the saved order.",
];

let responseIdx = 0;

async function init() {
    const response = await fetch("http://localhost:8080/conversation", {
     			method: "GET",
     			headers: { "Content-Type": "application/json" }
     		});
        conversations = await response.json();

      activeConvId = conversations[0].id;
      renderSidebar();
      renderMessages();
}

function save() {
  localStorage.setItem('testiq_ai_convs', JSON.stringify(conversations));
}

function now() {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}


async function createNewConversation() {
      const response = await fetch("http://localhost:8080/conversation/new", {
  			method: "POST",
  			headers: { "Content-Type": "application/json" }
  		});
  		const conv = await response.json();

  activeConvId = conv.id;
  renderSidebar();
  renderMessages();
}

function switchConversation(id) {
  activeConvId = id;
  renderSidebar();
  renderMessages();
  document.getElementById('current-conv-title').textContent = getActiveConv()?.title || 'Testiq AI';
}

function getActiveConv() {
  return conversations.find(c => c.id === activeConvId);
}

function deleteConversation(id) {
  conversations = conversations.filter(c => c.id !== id);
  if (activeConvId === id) {
    activeConvId = conversations[0]?.id || null;
    if (!conversations.length) {
      createNewConversation();
      return;
    }
  }
  save();
  renderSidebar();
  renderMessages();
}

function startRename(id, el) {
  const conv = conversations.find(c => c.id === id);
  if (!conv) return;

  const nameSpan = el.querySelector('.conv-name');
  const orig = conv.name;

  const inp = document.createElement('input');
  inp.className = 'conv-rename-input';
  inp.value = orig;
  nameSpan.replaceWith(inp);
  inp.focus();
  inp.select();

  function commit() {
    const val = inp.value.trim() || orig;
    conv.name = val;
    save();
    renderSidebar();
    if (activeConvId === id) {
      document.getElementById('current-conv-title').textContent = val;
    }
  }

  inp.addEventListener('blur', commit);
  inp.addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); inp.blur(); }
    if (e.key === 'Escape') { inp.value = orig; inp.blur(); }
  });
}


function renderSidebar() {
  const list = document.getElementById('conversations-list');
  list.innerHTML = '';
  console.log(conversations);
  conversations.forEach(conv => {
    const li = document.createElement('div');
    li.className = 'conv-item' + (conv.id === activeConvId ? ' active' : '');
    li.dataset.id = conv.id;
    li.innerHTML = `
      <span class="conv-icon">💬</span>
      <span class="conv-name">${escHtml(conv.title)}</span>
      <div class="conv-actions">
        <button class="conv-action-btn" title="Rename" onclick="event.stopPropagation(); startRename(${conv.id}, this.closest('.conv-item'))">✎</button>
        <button class="conv-action-btn" title="Delete" onclick="event.stopPropagation(); deleteConversation(${conv.id})">🗑</button>
      </div>
    `;

    li.addEventListener('click', () => switchConversation(conv.id));
    li.addEventListener('contextmenu', e => showContextMenu(e, conv.id));
    list.appendChild(li);
  });
}

function renderMessages() {
  const area = document.getElementById('messages-area');
  const conv = getActiveConv();

  document.getElementById('current-conv-title').textContent = conv?.name || 'Testiq AI';

  if (!conv || !conv.messages.length) {
    area.innerHTML = `
      <div class="empty-chat-state">
        <div class="empty-chat-icon">🤖</div>
        <div class="empty-chat-title">How can I help?</div>
        <div class="empty-chat-sub">Ask anything about your test suites, API checks, or prechecks.</div>
        <div class="quick-prompt-chips">
          <span class="quick-chip" onclick="quickSend('How do I use prechecks?')">Prechecks?</span>
          <span class="quick-chip" onclick="quickSend('What check types are available?')">Check types</span>
          <span class="quick-chip" onclick="quickSend('Explain parent test dependencies')">Parents</span>
        </div>
      </div>`;
    return;
  }

  area.innerHTML = conv.messages.map(msg => `
    <div class="message-group ${msg.role}">
      <span class="msg-sender">${msg.role === 'user' ? 'You' : 'Testiq AI'}</span>
      <div class="message-bubble">${escHtml(msg.text)}</div>
      <span class="msg-time">${msg.time}</span>
    </div>
  `).join('');

  area.scrollTop = area.scrollHeight;
}

 function createConversationObject(name, messages = []) {
 const conv = { id: Date.now() + Math.random(), name, messages };
  conversations.unshift(conv);
  save();
  return conv;
}
function sendMessage() {
  const input = document.getElementById('chat-input');
  const text = input.value.trim();
  if (!text) return;

  let conv = getActiveConv();
  if (!conv) { createNewConversation(); conv = getActiveConv(); }

  if (conv.messages.filter(m => m.role === 'user').length === 0 && conv.name === 'New Chat') {
    conv.name = text.length > 28 ? text.slice(0, 28) + '…' : text;
    document.getElementById('current-conv-title').textContent = conv.name;
  }

  conv.messages.push({ role: 'user', text, time: now() });
  save();
  input.value = '';
  input.style.height = 'auto';
  renderMessages();
  renderSidebar();

  const area = document.getElementById('messages-area');
  const typingEl = document.createElement('div');
  typingEl.className = 'message-group ai';
  typingEl.id = 'typing-indicator';
  typingEl.innerHTML = `<span class="msg-sender">Testiq AI</span><div class="typing-indicator"><div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div></div>`;
  area.appendChild(typingEl);
  area.scrollTop = area.scrollHeight;

  document.getElementById('send-btn').disabled = true;

  const delay = 900 + Math.random() * 800;
  setTimeout(() => {
    const typing = document.getElementById('typing-indicator');
    if (typing) typing.remove();

    const aiText = AI_RESPONSES[responseIdx % AI_RESPONSES.length];
    responseIdx++;

    conv.messages.push({ role: 'ai', text: aiText, time: now() });
    save();
    renderMessages();
    renderSidebar();
    document.getElementById('send-btn').disabled = false;
  }, delay);
}

function quickSend(text) {
  document.getElementById('chat-input').value = text;
  sendMessage();
}

function handleInputKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
}

function autoResize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 120) + 'px';
}

function toggleChat() {
  chatOpen = !chatOpen;
  const fab = document.getElementById('ai-fab');
  const container = document.getElementById('ai-chat-container');
  const iconAI = document.getElementById('fab-icon-ai');
  const iconClose = document.getElementById('fab-icon-close');

  if (chatOpen) {
    fab.classList.add('open');
    container.classList.add('visible');
    iconAI.style.display = 'none';
    iconClose.style.display = 'block';
    setTimeout(() => document.getElementById('chat-input').focus(), 350);
  } else {
    fab.classList.remove('open');
    container.classList.remove('visible');
    iconAI.style.display = 'block';
    iconClose.style.display = 'none';
  }
}

function toggleSidebar() {
  sidebarOpen = !sidebarOpen;
  document.getElementById('chat-sidebar').classList.toggle('collapsed', !sidebarOpen);
}

function showContextMenu(e, id) {
  e.preventDefault();
  contextMenuTarget = id;
  const menu = document.getElementById('context-menu');
  menu.style.display = 'block';
  menu.style.left = e.clientX + 'px';
  menu.style.top = e.clientY + 'px';
}

function hideContextMenu() {
  document.getElementById('context-menu').style.display = 'none';
  contextMenuTarget = null;
}

function ctxRename() {
  if (!contextMenuTarget) return;
  const el = document.querySelector(`.conv-item[data-id="${contextMenuTarget}"]`);
  if (el) startRename(contextMenuTarget, el);
  hideContextMenu();
}

function ctxDelete() {
  if (!contextMenuTarget) return;
  deleteConversation(contextMenuTarget);
  hideContextMenu();
}

document.addEventListener('click', hideContextMenu);

function escHtml(str) {
  return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

init();
