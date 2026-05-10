const form = document.getElementById("chatForm");
const input = document.getElementById("messageInput");
const messages = document.getElementById("messages");
const newChatButton = document.getElementById("newChatButton");
const clearHistoryButton = document.getElementById("clearHistoryButton");
const clearChatButton = document.getElementById("clearChatButton");
const exportChatButton = document.getElementById("exportChatButton");
const sessionList = document.getElementById("sessionList");
const micButton = document.getElementById("micButton");
const voiceToggleButton = document.getElementById("voiceToggleButton");
const sendButton = form.querySelector("button[type='submit']");
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const STORAGE_KEY = "hari.chat.sessions";
let recognition = null;
let speakReplies = false;
let isSending = false;
let sessions = loadSessions();
let currentSessionId = sessions[0]?.id || createSession().id;

if (SpeechRecognition) {
  recognition = new SpeechRecognition();
  recognition.lang = "en-IN";
  recognition.interimResults = false;
  recognition.continuous = false;

  recognition.addEventListener("start", function () {
    micButton.classList.add("listening");
    micButton.textContent = "Listening";
  });

  recognition.addEventListener("end", function () {
    micButton.classList.remove("listening");
    micButton.textContent = "Mic";
  });

  recognition.addEventListener("result", async function (event) {
    const transcript = event.results[0][0].transcript.trim();
    input.value = transcript;
    await sendMessage(transcript);
  });
} else {
  micButton.disabled = true;
  micButton.title = "Voice input is not supported in this browser";
}

form.addEventListener("submit", async function (event) {
  event.preventDefault();

  const text = input.value.trim();
  if (!text) {
    return;
  }

  await sendMessage(text);
});

messages.addEventListener("click", async function (event) {
  const copyButton = event.target.closest("[data-copy-message]");
  if (copyButton) {
    const messageId = copyButton.dataset.copyMessage;
    const session = getCurrentSession();
    const savedMessage = session.messages.find(function (message) {
      return message.id === messageId;
    });
    if (savedMessage) {
      await navigator.clipboard.writeText(savedMessage.text);
      copyButton.textContent = "Copied";
      setTimeout(function () {
        copyButton.textContent = "Copy";
      }, 1200);
    }
    return;
  }

  const button = event.target.closest("[data-prompt]");
  if (!button) {
    return;
  }

  await sendMessage(button.dataset.prompt);
});

sessionList.addEventListener("click", function (event) {
  const item = event.target.closest("[data-session-id]");
  if (!item) {
    return;
  }

  currentSessionId = item.dataset.sessionId;
  renderCurrentSession();
  renderSessionList();
});

document.addEventListener("click", async function (event) {
  const button = event.target.closest(".nav-item[data-prompt], .tool-stack [data-prompt]");
  if (!button) {
    return;
  }

  await sendMessage(button.dataset.prompt);
});

newChatButton.addEventListener("click", function () {
  currentSessionId = createSession().id;
  saveSessions();
  renderCurrentSession();
  renderSessionList();
  input.focus();
});

clearHistoryButton.addEventListener("click", function () {
  const confirmed = window.confirm("Clear all saved conversations?");
  if (!confirmed) {
    return;
  }

  sessions = [];
  currentSessionId = createSession().id;
  saveSessions();
  renderCurrentSession();
  renderSessionList();
});

clearChatButton.addEventListener("click", function () {
  const session = getCurrentSession();
  session.messages = [];
  session.title = "New conversation";
  saveSessions();
  renderCurrentSession();
  renderSessionList();
});

exportChatButton.addEventListener("click", function () {
  const session = getCurrentSession();
  const content = session.messages.map(function (message) {
    return `${message.sender}: ${message.text}`;
  }).join("\n\n");
  const file = new Blob([content || "Empty Hari conversation"], { type: "text/plain" });
  const url = URL.createObjectURL(file);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${session.title.replace(/[^a-z0-9]/gi, "-").toLowerCase() || "hari-chat"}.txt`;
  link.click();
  URL.revokeObjectURL(url);
});

micButton.addEventListener("click", function () {
  if (!recognition) {
    addMessage("Hari", "Voice input is not supported in this browser. Try Chrome or Edge.", "hari");
    return;
  }

  recognition.start();
});

voiceToggleButton.addEventListener("click", function () {
  speakReplies = !speakReplies;
  voiceToggleButton.textContent = speakReplies ? "Voice on" : "Voice off";

  if (!speakReplies) {
    window.speechSynthesis.cancel();
  }
});

async function sendMessage(text) {
  if (isSending) {
    return;
  }

  isSending = true;
  sendButton.disabled = true;
  micButton.disabled = true;
  removeWelcome();
  const userMessage = saveMessage("You", text, "user");
  addMessage("You", text, "user", userMessage.id);
  input.value = "";
  input.focus();
  const typing = addTypingIndicator();

  try {
    const response = await fetch("/api/hari/chat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ message: text })
    });

    const data = await response.json();
    typing.remove();
    const hariMessage = saveMessage("Hari", data.reply, "hari");
    addMessage("Hari", data.reply, "hari", hariMessage.id);
    speak(data.reply);
  } catch (error) {
    const errorMessage = "I cannot reach Hari's server right now. Check that the app is running.";
    typing.remove();
    const hariMessage = saveMessage("Hari", errorMessage, "hari");
    addMessage("Hari", errorMessage, "hari", hariMessage.id);
    speak(errorMessage);
  } finally {
    isSending = false;
    sendButton.disabled = false;
    micButton.disabled = !recognition;
  }
}

function addMessage(sender, text, className, messageId) {
  const div = document.createElement("div");
  div.className = "message " + className;
  div.innerHTML = `
    <div class="bubble">
      <span class="sender">${sender}</span>
      <div class="bubble-content"></div>
      ${className === "hari" ? `<div class="message-actions"><button class="message-action" type="button" data-copy-message="${messageId || ""}">Copy</button></div>` : ""}
    </div>
  `;
  renderMessageContent(div.querySelector(".bubble-content"), text);
  messages.appendChild(div);
  messages.scrollTop = messages.scrollHeight;
}

function addTypingIndicator() {
  const div = document.createElement("div");
  div.className = "message hari typing";
  div.innerHTML = `
    <div class="bubble">
      <span class="sender">Hari</span>
      <div class="typing-dots" aria-label="Hari is typing">
        <span></span><span></span><span></span>
      </div>
    </div>
  `;
  messages.appendChild(div);
  messages.scrollTop = messages.scrollHeight;
  return div;
}

function renderMessageContent(container, text) {
  const safeText = escapeHtml(text);
  const blocks = safeText.split(/```/);
  container.innerHTML = blocks.map(function (block, index) {
    if (index % 2 === 1) {
      return `<pre><code>${block.trim()}</code></pre>`;
    }

    return block
      .split(/\n{2,}/)
      .map(function (paragraph) {
        const withInlineCode = paragraph.replace(/`([^`]+)`/g, "<code>$1</code>");
        const withBold = withInlineCode.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
        return `<p>${withBold.replace(/\n/g, "<br>")}</p>`;
      })
      .join("");
  }).join("");
}

function escapeHtml(text) {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function removeWelcome() {
  const welcome = messages.querySelector(".welcome");
  if (welcome) {
    welcome.remove();
  }
}

function createSession() {
  const session = {
    id: String(Date.now()),
    title: "New conversation",
    createdAt: new Date().toISOString(),
    messages: []
  };
  sessions.unshift(session);
  return session;
}

function getCurrentSession() {
  let session = sessions.find(function (item) {
    return item.id === currentSessionId;
  });

  if (!session) {
    session = createSession();
    currentSessionId = session.id;
  }

  return session;
}

function saveMessage(sender, text, className) {
  const session = getCurrentSession();
  const message = {
    id: crypto.randomUUID ? crypto.randomUUID() : String(Date.now() + Math.random()),
    sender,
    text,
    className,
    createdAt: new Date().toISOString()
  };
  session.messages.push(message);

  if (session.title === "New conversation" && className === "user") {
    session.title = text.length > 42 ? text.slice(0, 42) + "..." : text;
  }

  sessions = [session].concat(sessions.filter(function (item) {
    return item.id !== session.id;
  }));
  saveSessions();
  renderSessionList();
  return message;
}

function renderCurrentSession() {
  const session = getCurrentSession();
  messages.innerHTML = "";

  if (session.messages.length === 0) {
    messages.innerHTML = welcomeMarkup("New chat started.", "Hari's saved memory, notes, and tasks are still available.");
    return;
  }

  session.messages.forEach(function (message) {
    if (!message.id) {
      message.id = crypto.randomUUID ? crypto.randomUUID() : String(Date.now() + Math.random());
    }
    addMessage(message.sender, message.text, message.className, message.id);
  });
}

function renderSessionList() {
  if (sessions.length === 0) {
    sessionList.innerHTML = "";
    return;
  }

  sessionList.innerHTML = sessions.map(function (session) {
    const activeClass = session.id === currentSessionId ? " active" : "";
    const count = session.messages.length;
    return `
      <button class="session-item${activeClass}" type="button" data-session-id="${session.id}">
        <span class="session-title"></span>
        <span class="session-meta">${count} message${count === 1 ? "" : "s"}</span>
      </button>
    `;
  }).join("");

  sessions.forEach(function (session, index) {
    const title = sessionList.querySelectorAll(".session-title")[index];
    if (title) {
      title.textContent = session.title;
    }
  });
}

function welcomeMarkup(title, body) {
  return `
    <div class="welcome">
      <div class="welcome-mark">H</div>
      <h3>${title}</h3>
      <p>${body}</p>
      <div class="suggestions">
        <button type="button" data-prompt="show memory">Show memory</button>
        <button type="button" data-prompt="show notes">Show notes</button>
        <button type="button" data-prompt="show tasks">Show tasks</button>
        <button type="button" data-prompt="help">Help</button>
      </div>
    </div>
  `;
}

function loadSessions() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
    return Array.isArray(saved) ? saved : [];
  } catch (error) {
    return [];
  }
}

function saveSessions() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions));
}

function speak(text) {
  if (!speakReplies || !("speechSynthesis" in window)) {
    return;
  }

  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "en-IN";
  utterance.rate = 1;
  utterance.pitch = 1;
  window.speechSynthesis.speak(utterance);
}

renderCurrentSession();
renderSessionList();
