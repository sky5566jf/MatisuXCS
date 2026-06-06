package com.matisu.xcs.server

import android.content.Context

object WebUI {
    fun getHTML(context: Context): String {
        val port = 8888
        return """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>MatisuXCS</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#1a1a2e;color:#e0e0e0;font-family:system-ui,sans-serif;display:flex;flex-direction:column;height:100dvh;overflow:hidden}
#toolbar{display:flex;flex-wrap:wrap;gap:6px;padding:8px;background:#16213e;border-bottom:1px solid #0f3460}
#toolbar button{flex:1;min-width:48px;padding:8px 6px;background:#0f3460;color:#e0e0e0;border:none;border-radius:6px;font-size:13px;cursor:pointer;white-space:nowrap}
#toolbar button:active{background:#e94560}
#toolbar button.danger{background:#533483}
#toolbar button.danger:active{background:#e94560}
#screen-container{flex:1;overflow:hidden;position:relative;background:#000;display:flex;align-items:center;justify-content:center}
#screen{max-width:100%;max-height:100%;object-fit:contain;touch-action:none}
#status{display:flex;gap:12px;padding:6px 12px;background:#16213e;font-size:12px;color:#90a4ae;justify-content:space-between}
.dot{width:8px;height:8px;border-radius:50%;display:inline-block;margin-right:4px}
.dot.on{background:#4caf50}
.dot.off{background:#f44336}
</style>
</head>
<body>
<div id="toolbar">
  <button onclick="sendKey('home')">桌面</button>
  <button onclick="sendKey('back')">返回</button>
  <button onclick="sendKey('recents')">多任务</button>
  <button onclick="sendKey('notifications')">通知</button>
  <button onclick="sendKey('lock')">锁屏</button>
  <button onclick="sendKey('power')">电源</button>
  <button onclick="sendKey('screenshot')">截屏</button>
  <button class="danger" onclick="toggleStream()" id="streamBtn">▶ 实时流</button>
</div>
<div id="screen-container">
  <img id="screen" src="/screenshot" alt="Screen">
</div>
<div id="status">
  <span><span class="dot on" id="statusDot"></span><span id="statusText">就绪</span></span>
  <span id="coords"></span>
</div>

<script>
const img = document.getElementById('screen');
const coords = document.getElementById('coords');
const streamBtn = document.getElementById('streamBtn');
let streaming = false;
let ws = null;
let swipeStart = null;
let swipeStartTime = 0;
let lastTapTime = 0;
const TAP_THRESHOLD = 10;
const SWIPE_MIN_DIST = 15;

function getRelXY(e) {
  const rect = img.getBoundingClientRect();
  const scaleX = img.naturalWidth / rect.width;
  const scaleY = img.naturalHeight / rect.height;
  const clientX = e.touches ? e.touches[0].clientX : e.clientX;
  const clientY = e.touches ? e.touches[0].clientY : e.clientY;
  return {
    x: Math.round((clientX - rect.left) * scaleX),
    y: Math.round((clientY - rect.top) * scaleY)
  };
}

function send(cmd, params) {
  const msg = params ? cmd + ' ' + params : cmd;
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(msg);
  } else {
    fetch('/' + cmd, {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body: params
    }).catch(() => {});
  }
}

function post(path, body) {
  fetch(path, {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: new URLSearchParams(body).toString()
  }).catch(() => {});
}

img.addEventListener('mousedown', e => {
  const {x, y} = getRelXY(e);
  swipeStart = {x, y};
  swipeStartTime = Date.now();
  post('/touch/down', {x, y});
  e.preventDefault();
});

img.addEventListener('mousemove', e => {
  if (!swipeStart) return;
  const {x, y} = getRelXY(e);
  coords.textContent = x + ',' + y;
  if (Math.abs(x - swipeStart.x) > SWIPE_MIN_DIST || Math.abs(y - swipeStart.y) > SWIPE_MIN_DIST) {
    post('/touch/move', {x, y});
  }
});

img.addEventListener('mouseup', e => {
  if (!swipeStart) return;
  const {x, y} = getRelXY(e);
  const dx = Math.abs(x - swipeStart.x);
  const dy = Math.abs(y - swipeStart.y);
  const dt = Date.now() - swipeStartTime;

  if (dx < TAP_THRESHOLD && dy < TAP_THRESHOLD && dt < 300) {
    post('/tap', {x, y});
  } else {
    post('/swipe', {x1: swipeStart.x, y1: swipeStart.y, x2: x, y2: y, duration: Math.max(dt, 100)});
  }
  post('/touch/up', {x, y});
  swipeStart = null;
});

img.addEventListener('touchstart', e => {
  const {x, y} = getRelXY(e);
  swipeStart = {x, y};
  swipeStartTime = Date.now();
  post('/touch/down', {x, y});
  e.preventDefault();
});

img.addEventListener('touchmove', e => {
  if (!swipeStart) return;
  const {x, y} = getRelXY(e);
  coords.textContent = x + ',' + y;
  e.preventDefault();
});

img.addEventListener('touchend', e => {
  if (!swipeStart) return;
  const touch = e.changedTouches[0];
  const rect = img.getBoundingClientRect();
  const scaleX = img.naturalWidth / rect.width;
  const scaleY = img.naturalHeight / rect.height;
  const x = Math.round((touch.clientX - rect.left) * scaleX);
  const y = Math.round((touch.clientY - rect.top) * scaleY);
  const dx = Math.abs(x - swipeStart.x);
  const dy = Math.abs(y - swipeStart.y);
  const dt = Date.now() - swipeStartTime;

  if (dx < TAP_THRESHOLD && dy < TAP_THRESHOLD && dt < 300) {
    post('/tap', {x, y});
  } else {
    post('/swipe', {x1: swipeStart.x, y1: swipeStart.y, x2: x, y2: y, duration: Math.max(dt, 100)});
  }
  post('/touch/up', {x, y});
  swipeStart = null;
  e.preventDefault();
});

function sendKey(key) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send('key ' + key);
  } else {
    post('/key', {key});
  }
}

function toggleStream() {
  if (streaming) {
    stopStream();
  } else {
    startStream();
  }
}

function startStream() {
  streaming = true;
  streamBtn.textContent = '⏸ 停止流';
  streamBtn.classList.add('danger');

  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  ws = new WebSocket(proto + '//' + location.host + '/');

  ws.onopen = () => {
    ws.send('start_stream');
    document.getElementById('statusDot').className = 'dot on';
    document.getElementById('statusText').textContent = '实时流';
  };

  ws.onmessage = (e) => {
    const msg = e.data;
    if (msg.startsWith('frame ')) {
      img.src = 'data:image/jpeg;base64,' + msg.substring(6);
    }
  };

  ws.onclose = () => {
    streaming = false;
    streamBtn.textContent = '▶ 实时流';
    streamBtn.classList.remove('danger');
    document.getElementById('statusDot').className = 'dot on';
    document.getElementById('statusText').textContent = '就绪';
    ws = null;
  };

  ws.onerror = () => {
    stopStream();
  };
}

function stopStream() {
  if (ws) {
    ws.send('stop_stream');
    ws.close();
    ws = null;
  }
  streaming = false;
  streamBtn.textContent = '▶ 实时流';
  streamBtn.classList.remove('danger');
  document.getElementById('statusDot').className = 'dot on';
  document.getElementById('statusText').textContent = '就绪';
}

function refreshScreen() {
  if (!streaming) {
    img.src = '/screenshot?' + Date.now();
  }
}

setInterval(refreshScreen, streaming ? 10000 : 1000);

document.addEventListener('keydown', e => {
  switch(e.key) {
    case 'Escape': sendKey('back'); break;
    case 'Home': case 'F1': sendKey('home'); break;
    case 'F2': sendKey('recents'); break;
    case 'F3': sendKey('notifications'); break;
    case 'F5': refreshScreen(); e.preventDefault(); break;
  }
});
</script>
</body>
</html>
""".trimIndent()
    }
}
