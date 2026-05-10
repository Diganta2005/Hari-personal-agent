const form = document.getElementById("chatForm");
const input = document.getElementById("messageInput");
const messages = document.getElementById("messages");
const newChatButton = document.getElementById("newChatButton");
const micButton = document.getElementById("micButton");
const voiceToggleButton = document.getElementById("voiceToggleButton");
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
let recognition = null;
let speakReplies = false;

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
  const button = event.target.closest("[data-prompt]");
  if (!button) {
    return;
  }

  await sendMessage(button.dataset.prompt);
});

document.addEventListener("click", async function (event) {
  const button = event.target.closest(".nav-item[data-prompt]");
  if (!button) {
    return;
  }

  await sendMessage(button.dataset.prompt);
});

newChatButton.addEventListener("click", function () {
  messages.innerHTML = `
    <div class="welcome">
      <div class="welcome-mark">H</div>
      <h3>New chat started.</h3>
      <p>Hari's saved memory, notes, and tasks are still available.</p>
      <div class="suggestions">
        <button type="button" data-prompt="show memory">Show memory</button>
        <button type="button" data-prompt="show notes">Show notes</button>
        <button type="button" data-prompt="show tasks">Show tasks</button>
        <button type="button" data-prompt="help">Help</button>
      </div>
    </div>
  `;
  input.focus();
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
  removeWelcome();
  addMessage("You", text, "user");
  input.value = "";
  input.focus();

  try {
    const response = await fetch("/api/hari/chat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ message: text })
    });

    const data = await response.json();
    addMessage("Hari", data.reply, "hari");
    speak(data.reply);
  } catch (error) {
    const errorMessage = "I cannot reach Hari's server right now. Check that the app is running.";
    addMessage("Hari", errorMessage, "hari");
    speak(errorMessage);
  }
}

function addMessage(sender, text, className) {
  const div = document.createElement("div");
  div.className = "message " + className;
  div.innerHTML = `
    <div class="bubble">
      <span class="sender">${sender}</span>
      <span></span>
    </div>
  `;
  div.querySelector(".bubble span:last-child").textContent = text;
  messages.appendChild(div);
  messages.scrollTop = messages.scrollHeight;
}

function removeWelcome() {
  const welcome = messages.querySelector(".welcome");
  if (welcome) {
    welcome.remove();
  }
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
