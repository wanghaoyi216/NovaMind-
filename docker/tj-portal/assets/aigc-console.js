(function () {
  var form = document.getElementById("chatForm");
  var messages = document.getElementById("messages");
  var timeline = document.getElementById("timeline");
  var sendBtn = document.getElementById("sendBtn");
  var stopBtn = document.getElementById("stopBtn");
  var controller = null;
  var currentAssistant = null;

  hydrateAuth();

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    startChat();
  });

  stopBtn.addEventListener("click", function () {
    if (controller) {
      controller.abort();
    }
    stopGeneration();
  });

  function hydrateAuth() {
    var tokenKeys = ["authorization", "Authorization", "access_token", "token", "tj_token"];
    for (var i = 0; i < tokenKeys.length; i++) {
      var value = localStorage.getItem(tokenKeys[i]) || sessionStorage.getItem(tokenKeys[i]);
      if (value) {
        document.getElementById("authorization").value = value.indexOf("Bearer ") === 0 ? value : "Bearer " + value;
        break;
      }
    }
  }

  async function startChat() {
    var question = document.getElementById("question").value.trim();
    var sessionId = document.getElementById("sessionId").value.trim() || "console-session";
    if (!question) {
      return;
    }

    controller = new AbortController();
    sendBtn.disabled = true;
    timeline.innerHTML = "";
    currentAssistant = addMessage("assistant", "");
    addMessage("user", question);

    try {
      var response = await fetch("/ais/chat", {
        method: "POST",
        headers: buildHeaders(),
        body: JSON.stringify(buildPayload(question, sessionId)),
        signal: controller.signal
      });
      if (!response.ok || !response.body) {
        throw new Error("请求失败：" + response.status);
      }
      await readSse(response.body);
    } catch (error) {
      if (error.name !== "AbortError") {
        appendTimeline("ERROR", "ERROR", error.message || String(error));
      }
    } finally {
      sendBtn.disabled = false;
      controller = null;
    }
  }

  function buildHeaders() {
    var headers = {
      "Content-Type": "application/json",
      "Accept": "text/event-stream"
    };
    var authorization = document.getElementById("authorization").value.trim();
    if (authorization) {
      headers.Authorization = authorization;
    }
    return headers;
  }

  function buildPayload(question, sessionId) {
    var media = [];
    var type = document.getElementById("mediaType").value;
    var url = document.getElementById("mediaUrl").value.trim();
    var mimeType = document.getElementById("mimeType").value.trim();
    var text = document.getElementById("mediaText").value.trim();
    if (type || url || text) {
      media.push({
        type: type || "text",
        url: url,
        mimeType: mimeType,
        name: type ? type + "-input" : "text-input",
        text: text
      });
    }
    return {
      question: question,
      sessionId: sessionId,
      media: media,
      clientMetadata: {
        source: "nginx-ai-console",
        userAgent: navigator.userAgent
      }
    };
  }

  async function readSse(body) {
    var reader = body.getReader();
    var decoder = new TextDecoder("utf-8");
    var buffer = "";
    while (true) {
      var result = await reader.read();
      if (result.done) {
        break;
      }
      buffer += decoder.decode(result.value, { stream: true });
      var parts = buffer.split(/\n\n/);
      buffer = parts.pop() || "";
      parts.forEach(handleSseBlock);
    }
    if (buffer.trim()) {
      handleSseBlock(buffer);
    }
  }

  function handleSseBlock(block) {
    var lines = block.split(/\r?\n/);
    var data = lines
      .filter(function (line) { return line.indexOf("data:") === 0; })
      .map(function (line) { return line.slice(5).trim(); })
      .join("");
    if (!data || data === "[DONE]") {
      return;
    }
    try {
      handleEvent(JSON.parse(data));
    } catch (error) {
      appendAssistant(data);
    }
  }

  function handleEvent(event) {
    var name = event.eventName || eventNameOf(event.eventType);
    if (name === "DATA") {
      appendAssistant(event.eventData || "");
      return;
    }
    if (name === "METRICS") {
      updateMetrics(event.eventData || event.metadata || {});
      return;
    }
    if (name === "STOP") {
      appendTimeline("STOP", event.phase || "FINAL", "本轮响应结束");
      return;
    }
    appendTimeline(name, event.phase || name, stringify(event.eventData));
  }

  function eventNameOf(type) {
    var map = {
      1001: "DATA",
      1002: "STOP",
      1003: "PARAM",
      1101: "THOUGHT",
      1102: "ACTION",
      1103: "OBSERVATION",
      1104: "METRICS",
      1105: "MEDIA",
      1106: "RICH_CONTENT",
      1500: "ERROR"
    };
    return map[type] || "EVENT";
  }

  function addMessage(role, text) {
    var node = document.createElement("div");
    node.className = "message " + role;
    node.textContent = text || "";
    messages.appendChild(node);
    messages.scrollTop = messages.scrollHeight;
    return node;
  }

  function appendAssistant(text) {
    if (!currentAssistant) {
      currentAssistant = addMessage("assistant", "");
    }
    currentAssistant.textContent += text;
    messages.scrollTop = messages.scrollHeight;
  }

  function appendTimeline(name, phase, body) {
    var item = document.createElement("li");
    item.className = name;
    var title = document.createElement("div");
    title.className = "event-title";
    title.innerHTML = "<strong>" + escapeHtml(name) + "</strong><span>" + escapeHtml(phase || "") + "</span>";
    var content = document.createElement("div");
    content.className = "event-body";
    content.textContent = body || "";
    item.appendChild(title);
    item.appendChild(content);
    timeline.appendChild(item);
    timeline.scrollTop = timeline.scrollHeight;
  }

  function updateMetrics(metrics) {
    var throughput = metrics.throughputTokensPerSecond || 0;
    var promptTokens = metrics.promptTokens || 0;
    var completionTokens = metrics.completionTokens || 0;
    var ratio = Number(metrics.contextUsageRatio || 0);
    document.getElementById("throughput").textContent = throughput + " token/s";
    document.getElementById("promptTokens").textContent = promptTokens;
    document.getElementById("completionTokens").textContent = completionTokens;
    document.getElementById("contextUsage").textContent = Math.round(ratio * 100) + "%";
    document.getElementById("contextMeter").style.width = Math.min(100, Math.round(ratio * 100)) + "%";
  }

  function stringify(value) {
    if (value == null) {
      return "";
    }
    if (typeof value === "string") {
      return value;
    }
    return JSON.stringify(value, null, 2);
  }

  async function stopGeneration() {
    var sessionId = document.getElementById("sessionId").value.trim();
    if (!sessionId) {
      return;
    }
    try {
      await fetch("/ais/chat/stop?sessionId=" + encodeURIComponent(sessionId), {
        method: "POST",
        headers: buildHeaders()
      });
    } catch (error) {
      appendTimeline("ERROR", "STOP", error.message || String(error));
    }
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }
})();
