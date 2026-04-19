/* =============================================================
   OBG Platform — app.js
   Handles: auth, games, rooms, lobby (ready + auto-start),
   game launch via iframe, game-over modal, friends (online +
   invite + sent requests + delete), profile, admin.
   All API requests target the same-origin Spring Boot backend.
   ============================================================= */

/* ── Global state ───────────────────────────────────────────── */
let token           = localStorage.getItem('obg_token') || '';
let currentUser     = JSON.parse(localStorage.getItem('obg_user') || 'null');
let allGames        = []; // list of all games
let allTags         = []; // all game tags
let currentGameId   = null; // currently selected game ID
let currentGameData = null; // details of current game
let currentRoomId   = null; // current room ID
let currentRoomData = null; // details of current room
let joiningRoomId   = null; // room we're trying to join
let lobbyPollTimer  = null; // keeps refreshing room list
let invitePollTimer = null; // keeps checking for new invites
let heartbeatTimer  = null; // tells server "I'm still online"
let pendingInvite   = null; // invite waiting for response
let adminCharts     = {};  // charts in admin panel
let agTags = [], egTags = []; // game tags for admin


/* ── HTTP helper ────────────────────────────────────────────── */
// Send a request to the backend
async function api(method, path, body, isForm) {
    const headers = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;
    if (body && !isForm) headers['Content-Type'] = 'application/json';
    try {
        const r = await fetch(path, { method, headers,
            body: body ? (isForm ? body : JSON.stringify(body)) : undefined });
        return await r.json();
    } catch { return { success: false, message: 'Network error' }; }
}

/* ── Toast ──────────────────────────────────────────────────── */
// Show a popup message on screen
let _tt;
function toast(msg, type = 'ok') {
    const el = document.getElementById('toast');
    el.textContent = msg; el.className = 'toast show ' + type;
    clearTimeout(_tt); _tt = setTimeout(() => { el.className = 'toast'; }, 3200);
}

/* ── Session ────────────────────────────────────────────────── */
// Save login info to memory and local storage
function saveSession(d) {
    token = d.token;
    currentUser = { uid:d.uid, username:d.username, fullName:d.fullName, email:d.email, role:d.role };
    localStorage.setItem('obg_token', token);
    localStorage.setItem('obg_user', JSON.stringify(currentUser));
}
// Log out the current user
function doLogout() {
    stopLobbyPoll(); stopInvitePoll(); stopHeartbeat();
    const iframe = document.getElementById('game-iframe');
    if (iframe) iframe.src = '';
    window.removeEventListener('message', onGameMessage);
    token = ''; currentUser = null;
    localStorage.removeItem('obg_token'); localStorage.removeItem('obg_user');
    go('s-login');
}

/* ── Navigation ─────────────────────────────────────────────── */
// Switch to the main screen
function go(id) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById(id).classList.add('active');
}
function pnav(sec, btn) {
    stopLobbyPoll();
    document.querySelectorAll('#s-player .psec').forEach(s => s.classList.remove('active'));
    document.querySelectorAll('#psb .nitem').forEach(b => b.classList.remove('active'));
    document.getElementById('ps-' + sec)?.classList.add('active');
    if (btn) btn.classList.add('active');
    loadSec(sec);
}
function showP(id) {
    if (id !== 'ps-ri') stopLobbyPoll();
    document.querySelectorAll('#s-player .psec').forEach(s => s.classList.remove('active'));
    document.getElementById(id)?.classList.add('active');
}
function anav(sec, btn) {
    document.querySelectorAll('#s-admin .psec').forEach(s => s.classList.remove('active'));
    document.querySelectorAll('.asidebar .nitem').forEach(b => b.classList.remove('active'));
    document.getElementById('as-' + sec)?.classList.add('active');
    if (btn) btn.classList.add('active');
    loadAdminSec(sec);
}
// open a popup window
function openMod(id)  { document.getElementById(id).classList.add('open'); }
// close a popup window
function closeMod(id) { document.getElementById(id).classList.remove('open'); }
// click on the gray background behind a popup to close it
document.addEventListener('click', e => { if (e.target.classList.contains('moverlay')) e.target.classList.remove('open'); });
function v(id)         { return document.getElementById(id)?.value.trim() || ''; }
function setE(id, msg) { const el = document.getElementById(id); if (el) el.textContent = msg; }

/* =============================================================
   AUTH ( login / register)
   ============================================================= */
// Handle login
async function doLogin() {
    const u = v('lu'), p = v('lp');
    if (!u || !p) { setE('lerr', 'Please fill in all fields.'); return; }
    const r = await api('POST', '/api/auth/login', { username:u, password:p });
    if (r.success) { saveSession(r.data); startApp(); }
    else setE('lerr', r.message || 'Invalid credentials.');
}
// Handle registration
async function doReg() {
    const f=v('rf'), u=v('ru'), e=v('re'), p=v('rp'), p2=v('rp2');
    if (!f||!u||!e||!p) { setE('rerr','All fields are required.'); return; }
    if (p !== p2)     { setE('rerr','Passwords do not match.'); return; }
    if (p.length < 8) { setE('rerr','Password must be at least 8 characters.'); return; }
    const r = await api('POST', '/api/auth/register', { fullName:f, username:u, email:e, password:p });
    if (r.success) { saveSession(r.data); startApp(); }
    else setE('rerr', r.message || 'Registration failed.');
}
// Admin login
async function doAdLog() {
    const r = await api('POST', '/api/auth/login', { username:v('au'), password:v('ap') });
    if (r.success && r.data.role === 'ADMIN') { saveSession(r.data); startApp(); }
    else setE('aerr', r.message || 'Invalid admin credentials.');
}
// Start the app after successful login
function startApp() {
    if (currentUser.role === 'ADMIN') {
        // for admin
        go('s-admin'); // go to admin panel
        document.getElementById('asAdminName').textContent = currentUser.username;
        anav('adash', document.querySelector('.asidebar .nitem'));
    } else {
        // for common player
        document.getElementById('sb-uname').textContent = currentUser.fullName || currentUser.username;
        go('s-player'); // go to player center
        pnav('games', document.querySelector('#psb .nitem'));
        checkFriendBadge(); // check if there's a friend request notification
        startInvitePoll(); // start checking for invites
        startHeartbeat(); // start sending heartbeat
    }
}

/* =============================================================
   SECTION LOADER
   ============================================================= */
// load the selected section
async function loadSec(sec) {
    if (sec === 'games')   await loadGames();
    if (sec === 'mygames') await loadMyGames();
    if (sec === 'friends') await loadFriends();
    if (sec === 'profile') await loadProfile();
}

/* =============================================================
   HEARTBEAT — marks this user as online every 30s
   ============================================================= */
function startHeartbeat() {
    stopHeartbeat();
    api('POST', '/api/users/me/heartbeat'); // immediate first ping
    heartbeatTimer = setInterval(() => api('POST', '/api/users/me/heartbeat'), 30000); // then every 30 seconds
}
function stopHeartbeat() {
    if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
}

/* =============================================================
   GAMES PAGE
   ============================================================= */
let activePill = '';
async function loadGames() {
    // load games and tags from server
    const [gr, tr] = await Promise.all([api('GET','/api/games'), api('GET','/api/tags')]);
    allGames = gr.data||[]; allTags = tr.data||[];
    activePill = ''; renderPills(); renderGrid(allGames);
}
// show the tag filter buttons
function renderPills() {
    const bar = document.getElementById('cpills');
    bar.innerHTML = `<span class="cpill ${!activePill?'active':''}" onclick="selectPill('')">All</span>`;
    allTags.forEach(t => {
        const c = allGames.filter(g => g.tags.includes(t.name)).length;
        if (c > 0) bar.innerHTML += `<span class="cpill ${activePill===t.name?'active':''}" onclick="selectPill('${t.name}')">${t.name} (${c})</span>`;
    });
}
// filter games by selected tag
function selectPill(tag) { activePill=tag; renderPills(); renderGrid(tag?allGames.filter(g=>g.tags.includes(tag)):allGames); }
// search games by typing in the search box
function srchGames(q) {
    const lq=q.toLowerCase();
    renderGrid(allGames.filter(g=>g.title.toLowerCase().includes(lq)||g.author.toLowerCase().includes(lq)||g.tags.some(t=>t.toLowerCase().includes(lq))));
}
// display games as cards on the page
function renderGrid(games) {
    const grid=document.getElementById('ggrid');
    document.getElementById('gcnt').textContent = games.length+' game'+(games.length!==1?'s':'');
    if (!games.length) { grid.innerHTML='<div class="empty-state"><img src="icons/gamepad.svg" alt=""/><p>No games found.</p></div>'; return; }
    grid.innerHTML = games.map(g=>`
        <div class="gcard" onclick="openGameDetail(${g.id})">
            <div class="gcover">${g.coverImageUrl?`<img src="${g.coverImageUrl}" alt="${g.title}">`:'<span style="font-size:2.5rem">&#9823;</span>'}</div>
            <div class="gcardinfo"><h4>${g.title}</h4><div class="gauth">${g.author}</div>
            <div class="gtags">${g.tags.slice(0,3).map(t=>`<span class="gtag">${t}</span>`).join('')}</div></div>
        </div>`).join('');
}

/* =============================================================
   GAME DETAIL  — PDF references removed
   ============================================================= */
let _did = null;
// open the detail page for a game
async function openGameDetail(id) {
    _did = id;
    const [gr, lr] = await Promise.all([api('GET',`/api/games/${id}`), api('GET','/api/likes')]);
    const g = gr.data; if (!g) return;
    currentGameData = g;
    const liked = (lr.data||[]).map(Number).includes(Number(id));
    // fill all the details on the page
    document.getElementById('dtitle').textContent = g.title;
    document.getElementById('dauth').textContent  = 'by ' + g.author;
    document.getElementById('ddu').textContent    = g.durationMinutes + ' min';
    document.getElementById('dag').textContent    = g.minimumAge + '+';
    document.getElementById('ddesc').textContent  = g.description;
    document.getElementById('dtags').innerHTML    = g.tags.map(t=>`<span class="gtag">${t}</span>`).join('');
    document.getElementById('dart').innerHTML     = g.coverImageUrl ? `<img src="${g.coverImageUrl}" alt="${g.title}">` : '<span style="font-size:3rem">&#9823;</span>';
    document.getElementById('drules').innerHTML   = g.rules || '<p style="color:#999">No rules provided.</p>';
    const lb = document.getElementById('likebtn');
    lb.className = liked ? 'btn-green bsm' : 'btn-green-out bsm';
    lb.innerHTML = `<img src="icons/heart.svg" alt=""/> ${liked?'Liked':'Like'}`;
    showP('ps-det');  // show the game detail page
}
// like or unlike the current game
async function toggleLike() {
    if (!_did) return;
    const lb = document.getElementById('likebtn');
    const wasLiked = lb.classList.contains('btn-green');
    if (wasLiked) {
        // unlike: send DELETE request
        const r = await api('DELETE', `/api/likes/${_did}`);
        if (!r.success) { toast(r.message||'Failed to unlike','err'); return; }
        lb.className = 'btn-green-out bsm'; lb.innerHTML = '<img src="icons/heart.svg" alt=""/> Like';
        toast('Removed from liked games','info');
    } else {
        // like: send POST request
        const r = await api('POST', `/api/likes/${_did}`);
        if (!r.success) { toast(r.message||'Failed to like','err'); return; }
        lb.className = 'btn-green bsm'; lb.innerHTML = '<img src="icons/heart.svg" alt=""/> Liked';
        toast('Added to liked games!');
    }
}

/* =============================================================
   ROOMS
   ============================================================= */
// open the rooms page for the selected game
async function openRooms() {
    currentGameId = _did;
    const gr = await api('GET', `/api/games/${currentGameId}`);
    document.getElementById('rgtitle').textContent = 'Rooms — ' + (gr.data?.title||'');
    showP('ps-rooms'); await refreshRooms();
}
// get the latest list of rooms from the server
async function refreshRooms() {
    const res = await api('GET', `/api/rooms/game/${currentGameId}`);
    const rooms = res.data||[];
    const grid  = document.getElementById('rgrid');
    if (!rooms.length) { grid.innerHTML='<div class="empty-state"><img src="icons/gamepad.svg" alt=""/><p>No rooms yet. Create one!</p></div>'; return; }
    grid.innerHTML = rooms.map(r => {
        const full = r.currentPlayers >= r.maxPlayers;
        const cls  = r.status==='WAITING'&&!full ? 'waiting' : '';
        let btn = '';
        if      (r.isMember && r.status==='PLAYING')        btn=`<div style="margin-top:10px"><button class="btn-green bsm" onclick="rejoinGame(${r.id})"><img src="icons/play.svg" alt=""/> Return to Game</button></div>`;
        else if (r.isMember && r.status==='WAITING')         btn=`<div style="margin-top:10px"><button class="btn-green bsm" onclick="rejoinLobby(${r.id})"><img src="icons/play.svg" alt=""/> Return to Lobby</button></div>`;
        else if (!r.isMember && r.status==='WAITING'&&!full) btn=`<div style="margin-top:10px"><button class="btn-green bsm" onclick="joinRoom(${r.id},'${r.privacy}')"><img src="icons/play.svg" alt=""/> Join</button></div>`;
        return `<div class="rcard ${cls}"><h4>${r.roomName}</h4>
            <div class="rmeta"><span>Host: ${r.hostUsername}</span><span>${r.currentPlayers}/${r.maxPlayers}</span><span>${r.privacy}</span></div>
            <span class="rbadge ${r.status.toLowerCase()}">${r.status}</span>${btn}</div>`;
    }).join('');
}
// show/hide password field when creating a room
function tglRoomPw(val) { document.getElementById('crpwg').style.display = val==='PRIVATE'?'':'none'; }
// create a new game room
async function createRoom() {
    const name=v('crname')||'My Room', privacy=document.getElementById('crpriv').value, pw=v('crpw')||null;
    if (privacy==='PRIVATE' && !pw) { toast('Private rooms require a password','err'); return; }
    const res = await api('POST','/api/rooms',{gameId:currentGameId,roomName:name,privacy,password:pw,maxPlayers:2});
    if (!res.success) { toast(res.message||'Failed','err'); return; }
    closeMod('mc-room');
    document.getElementById('crname').value=''; document.getElementById('crpw').value='';
    currentRoomId=res.data.id; currentRoomData=res.data;
    fillLobby(res.data); showP('ps-ri'); startLobbyPoll(); updateInviteBanner();
}
// join a room
async function joinRoom(roomId, privacy) {
    if (privacy==='PRIVATE') { joiningRoomId=roomId; document.getElementById('jppw').value=''; openMod('m-joinp'); return; }
    const res = await api('POST',`/api/rooms/${roomId}/join`,{password:null});
    if (!res.success) { toast(res.message||'Cannot join','err'); return; }
    currentRoomId=roomId; currentRoomData=res.data;
    fillLobby(res.data); showP('ps-ri'); startLobbyPoll(); updateInviteBanner();
}
// join a private room with password
async function joinPriv() {
    const pw=v('jppw'); if(!pw){toast('Please enter the room password','err');return;}
    const res = await api('POST',`/api/rooms/${joiningRoomId}/join`,{password:pw});
    if (!res.success) { toast(res.message||'Wrong password','err'); return; }
    closeMod('m-joinp'); currentRoomId=joiningRoomId; currentRoomData=res.data;
    fillLobby(res.data); showP('ps-ri'); startLobbyPoll(); updateInviteBanner();
}
// rejoin a room lobby
async function rejoinLobby(roomId) {
    const res = await api('POST',`/api/rooms/${roomId}/join`,{password:null});
    if (!res.success) { toast(res.message||'Cannot rejoin','err'); return; }
    currentRoomId=roomId; currentRoomData=res.data;
    fillLobby(res.data); showP('ps-ri'); startLobbyPoll();
}
// rejoin a game that's already playing
async function rejoinGame(roomId) {
    const res = await api('POST',`/api/rooms/${roomId}/join`,{password:null});
    if (!res.success) { toast(res.message||'Cannot rejoin','err'); return; }
    currentRoomId=roomId; currentRoomData=res.data; enterGame();
}

/* =============================================================
   LOBBY  — polls every 2s; auto-starts when all players ready
   ============================================================= */
function fillLobby(r) {
    currentRoomData = r;
    document.getElementById('riname').textContent = r.roomName;
    document.getElementById('risub').textContent  = 'Host: ' + r.hostUsername;
    document.getElementById('ripl').textContent   = r.currentPlayers + '/' + r.maxPlayers;
    document.getElementById('ristat').textContent = r.status;
    document.getElementById('ripriv').textContent = r.privacy;

    const myPlayer  = r.players.find(p => p.username === currentUser.username);
    const iAmReady  = myPlayer?.isReady || false;
    const allReady  = r.players.length >= 2 && r.players.every(p => p.isReady);

    // Status message
    const statusEl = document.getElementById('lobby-status');
    if (r.players.length < 2) {
        statusEl.textContent = 'Waiting for players to join...';
        statusEl.className   = 'lobby-status-msg waiting waiting-pulse';
    } else if (!allReady) {
        const rc = r.players.filter(p => p.isReady).length;
        statusEl.textContent = `${rc}/${r.players.length} players ready — waiting for all to be ready...`;
        statusEl.className   = 'lobby-status-msg waiting';
    } else {
        statusEl.textContent = 'All players ready! Starting game...';
        statusEl.className   = 'lobby-status-msg ready-go';
    }

    // Ready button
    const readyBtn = document.getElementById('readyBtn');
    if (readyBtn) {
        readyBtn.textContent = iAmReady ? 'Cancel Ready' : 'Ready';
        readyBtn.className   = iAmReady ? 'btn-green bsm' : 'btn-grey bsm';
    }

    // Player list
    document.getElementById('pslots').innerHTML = r.players.map(p =>
        `<div class="pslot">
            <strong>${p.fullName || p.username}</strong>
            ${r.hostUsername===p.username?'<span style="font-size:.7rem;color:#888;margin-left:4px">(Host)</span>':''}
            <span class="ready-badge ${p.isReady?'ready':'not-ready'}">${p.isReady?'Ready':'Not Ready'}</span>
        </div>`
    ).join('');
}

function startLobbyPoll() {
    stopLobbyPoll();
    lobbyPollTimer = setInterval(async () => {
        if (!currentRoomId) { stopLobbyPoll(); return; }
        const res = await api('GET', `/api/rooms/${currentRoomId}`);
        if (!res.success || !res.data) { stopLobbyPoll(); return; }
        const r = res.data;
        fillLobby(r);
        // Auto-start when all players are ready
        const allReady = r.players.length >= 2 && r.players.every(p => p.isReady);
        if (allReady) { stopLobbyPoll(); enterGame(); }
    }, 2000);
}
function stopLobbyPoll() { if (lobbyPollTimer) { clearInterval(lobbyPollTimer); lobbyPollTimer = null; } }

async function toggleReady() {
    if (!currentRoomId) return;
    const res = await api('POST', `/api/rooms/${currentRoomId}/ready`);
    if (!res.success) { toast(res.message||'Failed','err'); return; }
    fillLobby(res.data);
}

async function leaveRoomBack() {
    stopLobbyPoll();
    if (currentRoomId) { await api('DELETE',`/api/rooms/${currentRoomId}/leave`); currentRoomId=null; currentRoomData=null; updateInviteBanner(); }
    showP('ps-rooms'); await refreshRooms();
}

/* =============================================================
   ENTER GAME  — load iframe, send OBG_INIT (platform only)
   ============================================================= */
function enterGame() {
    if (!currentRoomId || !currentGameData) return;
    stopLobbyPoll();

    const g      = currentGameData;
    const wsUrl  = `ws://${location.host}/ws/relay?roomId=${currentRoomId}&token=${token}`;
    const players = currentRoomData?.players || [];

    document.getElementById('ig-title').textContent = g.title;
    document.getElementById('ig-slots').innerHTML = players.map(p =>
        `<div class="pslot"><strong>${p.fullName||p.username}</strong>${p.uid?`<code class="uid-code">${p.uid}</code>`:''}</div>`
    ).join('');

    // Show loading spinner while iframe initialises
    const loading = document.getElementById('game-loading');
    if (loading) loading.classList.remove('hidden');

    const gameUrl = g.gameFileUrl || '/games/gomoku.html';
    const iframe  = document.getElementById('game-iframe');
    iframe.style.opacity = '0';
    iframe.src   = gameUrl;
    iframe.onload = () => {
        if (loading) loading.classList.add('hidden');
        iframe.style.opacity = '1';
        iframe.contentWindow.postMessage({
            type:    'OBG_INIT',
            roomId:  String(currentRoomId),
            myUid:   currentUser.uid,
            wsUrl,
            players: players.map(p => ({
                uid:      p.uid || '',
                username: p.username,
                fullName: p.fullName || p.username,
                isMe:     p.uid === currentUser.uid
            }))
        }, '*');
    };

    window.removeEventListener('message', onGameMessage);
    window.addEventListener('message', onGameMessage);

    // Hide sidebar and topbar during gameplay to prevent accidental navigation
    document.getElementById('psb').style.display = 'none';
    document.querySelector('#s-player .topbar').style.display = 'none';
    document.getElementById('pma').style.padding = '0';
    ///
    showP('ps-ingame');
}

function onGameMessage(event) {
    const msg = event.data;
    if (!msg || typeof msg !== 'object') return;
    if (msg.type === 'OBG_GAME_OVER') {
        window.removeEventListener('message', onGameMessage);
        showGameOverModal(msg.winner, msg.disconnected);
    }
}

function showGameOverModal(winnerUid, disconnected) {
    let icon, title, sub;
    if (disconnected)                               { icon='⚠️'; title='Disconnected'; sub='Lost connection to game server.'; }
    else if (!winnerUid)                            { icon='🤝'; title='Draw!'; sub='Well played by both sides.'; }
    else if (winnerUid === currentUser.uid)         { icon='🏆'; title='You Win!'; sub='Congratulations!'; }
    else                                            { icon='😔'; title='You Lost.'; sub='Better luck next time!'; }
    document.getElementById('go-icon').textContent  = icon;
    document.getElementById('go-title').textContent = title;
    document.getElementById('go-sub').textContent   = sub;
    openMod('m-gameover');
}

// Return to lobby — resets room ready states so a new round can begin
async function goReturnLobby() {
    restoreNav();
    closeMod('m-gameover');
    const iframe = document.getElementById('game-iframe');
    if (iframe) { iframe.src=''; iframe.style.opacity='1'; }
    if (currentRoomId) {
        // Reset room: sets status back to WAITING, clears all isReady flags
        const res = await api('POST', `/api/rooms/${currentRoomId}/reset`);
        const data = res.success ? res.data : (await api('GET', `/api/rooms/${currentRoomId}`)).data;
        if (data) { currentRoomData=data; fillLobby(data); showP('ps-ri'); startLobbyPoll(); return; }
    }
    showP('ps-rooms');
}

// Leave room entirely after game ends
async function goLeaveRoom() {
    restoreNav();
    closeMod('m-gameover');
    const iframe = document.getElementById('game-iframe');
    if (iframe) { iframe.src=''; iframe.style.opacity='1'; }
    if (currentRoomId) { await api('DELETE',`/api/rooms/${currentRoomId}/leave`); currentRoomId=null; currentRoomData=null; }
    showP('ps-rooms');
}

function leaveGame() {
    restoreNav();
    if (!confirm('Leave this game? This may count as a forfeit.')) return;
    const iframe = document.getElementById('game-iframe');
    if (iframe) { iframe.src=''; iframe.style.opacity='1'; }
    window.removeEventListener('message', onGameMessage);
    if (currentRoomId) { api('DELETE',`/api/rooms/${currentRoomId}/leave`); currentRoomId=null; currentRoomData=null; }
    showP('ps-rooms');
}

function wsResign() {
    if (!confirm('Resign this game?')) return;
    const iframe = document.getElementById('game-iframe');
    if (iframe?.contentWindow) iframe.contentWindow.postMessage({ type:'OBG_RESIGN' }, '*');
}

function restoreNav() {
    document.getElementById('psb').style.display = '';
    document.querySelector('#s-player .topbar').style.display = '';
    document.getElementById('pma').style.padding = '';
}

/* =============================================================
   MY GAMES  — liked + play history with result
   ============================================================= */
async function loadMyGames() {
    const [lr, hr, gr] = await Promise.all([api('GET','/api/likes'), api('GET','/api/likes/history'), api('GET','/api/games')]);
    const likedIds=(lr.data||[]).map(Number), hist=hr.data||[], gmap=Object.fromEntries((gr.data||[]).map(g=>[g.id,g]));
    const lgrid=document.getElementById('lgrid');
    if (!likedIds.length) lgrid.innerHTML='<div class="empty-state"><img src="icons/heart.svg" alt=""/><p>No liked games yet.</p></div>';
    else lgrid.innerHTML=likedIds.map(id=>{const g=gmap[id];if(!g)return'';return`<div class="gcard" onclick="openGameDetail(${g.id})"><div class="gcover">${g.coverImageUrl?`<img src="${g.coverImageUrl}" alt="">`:'<span style="font-size:2.5rem">&#9823;</span>'}</div><div class="gcardinfo"><h4>${g.title}</h4><div class="gauth">${g.author}</div></div></div>`;}).join('');
    const hlist=document.getElementById('hlist');
    if (!hist.length) hlist.innerHTML='<div class="empty-state"><img src="icons/clock.svg" alt=""/><p>No play history yet.</p></div>';
    else {
        const resultClass = { WIN:'result-win', LOSS:'result-loss', DRAW:'result-draw', UNKNOWN:'result-unknown' };
        const resultLabel = { WIN:'Win', LOSS:'Loss', DRAW:'Draw', UNKNOWN:'—' };
        hlist.innerHTML=`<table class="hist-table">
            <thead><tr><th>Game</th><th>Result</th><th>Played</th><th>Duration</th></tr></thead>
            <tbody>${hist.map(h=>`<tr>
                <td><strong>${h.gameTitle}</strong></td>
                <td><span class="result-badge ${resultClass[h.result]||'result-unknown'}">${resultLabel[h.result]||h.result}</span></td>
                <td>${h.playedAt}</td>
                <td>${h.durationMinutes} min</td>
            </tr>`).join('')}</tbody></table>`;
    }
}
function mgTab(tab,btn){document.querySelectorAll('.mgtab').forEach(b=>b.classList.remove('active'));document.querySelectorAll('.mgpane').forEach(p=>p.classList.remove('active'));btn?.classList.add('active');document.getElementById('mg-'+tab)?.classList.add('active');}
function srchLiked(q){const lq=q.toLowerCase();document.querySelectorAll('#lgrid .gcard').forEach(c=>{c.style.display=c.textContent.toLowerCase().includes(lq)?'':'none';});}

/* =============================================================
   FRIENDS  — online status, invite, sent requests, delete
   ============================================================= */
async function loadFriends() {
    const [fr, rr, sr] = await Promise.all([
        api('GET','/api/friends'),
        api('GET','/api/friends/requests'),
        api('GET','/api/friends/sent')
    ]);
    const friends=fr.data||[], reqs=rr.data||[], sent=sr.data||[];
    ['notif-dot','rbadge'].forEach(id=>{document.getElementById(id).style.display=reqs.length?'':'none';});

    // Active friends tab
    const list=document.getElementById('frlist-cards');
    if (!friends.length && !sent.length) {
        list.innerHTML='<div class="empty-state"><img src="icons/friends.svg" alt=""/><p>No friends yet. Add someone by UID!</p></div>';
    } else {
        const friendsHtml = friends.map(f => {
            const dot   = f.isOnline ? 'on' : 'off';
            const state = f.isOnline ? 'Online' : 'Offline';
            const invBtn = f.isOnline && currentRoomId
                ? `<button class="btn-green bsm" onclick="inviteFriend('${f.uid}')">Invite</button>`
                : '';
            return `<div class="fr-card">
                <span class="fr-online ${dot}" title="${state}"></span>
                <div style="flex:1">
                    <div class="fr-name">${f.fullName||f.username}</div>
                    <div class="fr-sub">@${f.username} &middot; <span style="color:${f.isOnline?'#5aaa38':'#aaa'}">${state}</span></div>
                </div>
                ${invBtn}
                <button class="btn-grey bsm" style="margin-left:6px" onclick="deleteFriend('${f.uid}','${f.fullName||f.username}')">Remove</button>
            </div>`;
        }).join('');
        // Pending sent requests (show "Pending" state to the sender)
        const sentHtml = sent.length ? `<div style="margin-top:16px"><div class="ri-label">Sent Requests (awaiting reply)</div>${
            sent.map(r=>`<div class="fr-card" style="opacity:.7">
                <span class="fr-online off"></span>
                <div style="flex:1">
                    <div class="fr-name">${r.addresseeFullName||r.addresseeUsername}</div>
                    <div class="fr-sub">@${r.addresseeUsername}</div>
                </div>
                <span class="ready-badge not-ready">Pending</span>
            </div>`).join('')
        }</div>` : '';
        list.innerHTML = friendsHtml + sentHtml;
    }
    updateInviteBanner();
}
function srchFriends(q){const lq=q.toLowerCase();document.querySelectorAll('#frlist-cards .fr-card').forEach(c=>{c.style.display=c.textContent.toLowerCase().includes(lq)?'':'none';});}

async function deleteFriend(uid, name) {
    if (!confirm(`Remove ${name} from your friends?`)) return;
    const res = await api('DELETE', `/api/friends/${uid}`);
    if (res.success) { toast('Friend removed','info'); await loadFriends(); }
    else toast(res.message||'Failed','err');
}

async function showFriendRequests() {
    const res=await api('GET','/api/friends/requests');const reqs=res.data||[];
    document.getElementById('notif-list').innerHTML=reqs.length
        ?reqs.map(r=>`<div style="display:flex;justify-content:space-between;align-items:center;padding:10px 0;border-bottom:1px solid #eee">
            <div><strong>${r.requesterFullName||r.requesterUsername}</strong><br>
            <span style="font-size:.78rem;color:#888">@${r.requesterUsername} &middot; ${r.requesterUid}</span></div>
            <div style="display:flex;gap:6px">
            <button class="btn-green bsm" onclick="acceptFR(${r.id})">Accept</button>
            <button class="btn-red bsm" onclick="rejectFR(${r.id})">Decline</button></div></div>`).join('')
        :'<p style="color:#888;text-align:center;padding:16px">No pending requests.</p>';
    openMod('m-notifs');
}
async function acceptFR(id){await api('PUT',`/api/friends/request/${id}/accept`);toast('Friend request accepted!');closeMod('m-notifs');await loadFriends();}
async function rejectFR(id){await api('PUT',`/api/friends/request/${id}/reject`);toast('Request declined.','info');closeMod('m-notifs');await loadFriends();}
async function addFr(){
    const uid=v('adduid');if(!uid)return;
    const res=await api('POST',`/api/friends/request/${uid}`);
    if(res.success){toast('Friend request sent! Awaiting reply.');closeMod('m-adduid');document.getElementById('adduid').value='';await loadFriends();}
    else toast(res.message||'Failed','err');
}
async function checkFriendBadge(){const res=await api('GET','/api/friends/requests');const n=(res.data||[]).length;['notif-dot','rbadge'].forEach(id=>{document.getElementById(id).style.display=n?'':'none';});}

// Invite a single friend — private rooms skip password requirement
async function inviteFriend(uid) {
    if (!currentRoomId) { toast('You are not in a room','info'); return; }
    const res = await api('POST','/api/invites/send',{ targetUid:uid, roomId:String(currentRoomId) });
    if (res.success) toast('Invite sent!'); else toast(res.message||'Failed to invite','err');
}
async function inviteAllOnlineFriends() {
    if (!currentRoomId) return;
    const fr = await api('GET','/api/friends');
    const online = (fr.data||[]).filter(f => f.isOnline);
    if (!online.length) { toast('No friends are online right now','info'); return; }
    for (const f of online) await api('POST','/api/invites/send',{ targetUid:f.uid, roomId:String(currentRoomId) });
    toast(`Invited ${online.length} online friend${online.length>1?'s':''}!`);
}
function updateInviteBanner() {
    const banner = document.getElementById('inv-banner'); if (!banner) return;
    if (currentRoomId && currentRoomData) {
        document.getElementById('inv-banner-text').textContent = `You are in room "${currentRoomData.roomName}".`;
        banner.style.display = '';
    } else { banner.style.display = 'none'; }
}

// Poll for incoming invites every 8s
function startInvitePoll() {
    stopInvitePoll();
    invitePollTimer = setInterval(pollInvites, 8000);
}
function stopInvitePoll(){if(invitePollTimer){clearInterval(invitePollTimer);invitePollTimer=null;}}
async function pollInvites() {
    if (!token||!currentUser) return;
    const res=await api('GET','/api/invites/poll');
    const invites=res.data||[];if(!invites.length)return;
    const inv=invites[invites.length-1];
    pendingInvite={ roomId:inv.roomId, gameId:inv.gameId };
    document.getElementById('inv-title').textContent='Game Invite';
    document.getElementById('inv-body').textContent=`${inv.fromName} invited you to join "${inv.roomName}" (${inv.gameTitle})`;
    openMod('m-invite');
}
async function acceptInvite() {
    closeMod('m-invite');if(!pendingInvite)return;
    // inviteBypass=true skips password check for private rooms
    const res=await api('POST',`/api/rooms/${pendingInvite.roomId}/join?inviteBypass=true`,{password:null});
    if(!res.success){toast(res.message||'Could not join','err');return;}
    const gRes=await api('GET',`/api/games/${res.data.gameId||pendingInvite.gameId}`);
    currentGameData=gRes.data; currentGameId=pendingInvite.gameId;
    currentRoomId=pendingInvite.roomId; currentRoomData=res.data;
    fillLobby(res.data); showP('ps-ri'); startLobbyPoll(); go('s-player');
}
function declineInvite(){closeMod('m-invite');pendingInvite=null;}

/* =============================================================
   PROFILE
   ============================================================= */
async function loadProfile() {
    const res=await api('GET','/api/users/me');const u=res.data;if(!u)return;
    currentUser={...currentUser,fullName:u.fullName,email:u.email};
    document.getElementById('puname').textContent  = u.fullName;
    document.getElementById('puuid').textContent   = u.uid;
    document.getElementById('piname').textContent  = u.fullName;
    document.getElementById('piuname').textContent = u.username;
    document.getElementById('piemail').textContent = u.email;
    document.getElementById('pireg').textContent   = u.registeredAt;
    document.getElementById('pitype').textContent  = u.role;
    document.getElementById('sb-uname').textContent = u.fullName || u.username;
}
function openEditProfile(){document.getElementById('epname').value=currentUser.fullName||'';document.getElementById('epemail').value=currentUser.email||'';openMod('m-editp');}
async function saveProf(){const res=await api('PUT','/api/users/me',{fullName:v('epname'),email:v('epemail')});if(res.success){closeMod('m-editp');toast('Profile updated!');await loadProfile();}else toast(res.message||'Update failed','err');}
async function chPw(){const o=v('cpold'),n=v('cpnew'),n2=v('cpnew2');if(n!==n2){setE('pwerr','Passwords do not match.');document.getElementById('pwerr').style.display='';return;}document.getElementById('pwerr').style.display='none';const res=await api('POST','/api/users/me/password',{oldPassword:o,newPassword:n,confirmPassword:n2});if(res.success){closeMod('m-chpw');toast('Password changed!');}else toast(res.message||'Failed','err');}
async function deleteMyAccount(){if(!confirm('Permanently delete your account? This cannot be undone.'))return;const res=await api('DELETE','/api/users/me');if(res.success){closeMod('m-delacc');toast('Account deleted.','info');doLogout();}else toast(res.message||'Failed','err');}
function pwStr(val,barId){const bar=document.getElementById(barId);if(!bar)return;const n=val.length;if(n<6){bar.style.width='20%';bar.style.background='#d63031';}else if(n<10){bar.style.width='55%';bar.style.background='#f39c12';}else{bar.style.width='100%';bar.style.background='#5aaa38';}}

/* =============================================================
   ADMIN
   ============================================================= */
async function loadAdminSec(sec){if(sec==='adash')await loadAdminDash();if(sec==='agames')await loadAdminGames();if(sec==='ausers')await loadAdminUsers();if(sec==='acats')await loadAdminTags();}
async function loadAdminDash(){
    const[sr,gr,lr]=await Promise.all([api('GET','/api/admin/stats/summary'),api('GET','/api/games'),api('GET','/api/admin/stats/user-growth')]);
    const s=sr.data||{};
    document.getElementById('asgames').textContent  = s.totalGames  ?? '—';
    document.getElementById('asusers').textContent  = s.totalUsers  ?? '—';
    document.getElementById('asactive').textContent = s.activeUsers ?? '—';
    document.getElementById('asrooms').textContent  = s.activeRooms ?? '—';
    const tc={};(gr.data||[]).forEach(g=>(g.tags||[]).forEach(t=>{tc[t]=(tc[t]||0)+1;}));
    renderPie(tc);renderLine(lr.data||[]);
}
const PIE_COLORS=['#5aaa38','#2980b9','#e74c3c','#f39c12','#9b59b6','#1abc9c','#e67e22','#34495e','#e91e63','#00bcd4'];
function renderPie(tc){const ctx=document.getElementById('piechart');if(!ctx)return;if(adminCharts.pie)adminCharts.pie.destroy();const labels=Object.keys(tc),data=Object.values(tc);adminCharts.pie=new Chart(ctx,{type:'doughnut',data:{labels,datasets:[{data,backgroundColor:PIE_COLORS,borderWidth:2,borderColor:'#fff'}]},options:{responsive:false,maintainAspectRatio:false,plugins:{legend:{display:false}}}});document.getElementById('pileg').innerHTML=labels.map((l,i)=>`<div class="pleg-item"><div class="pleg-dot" style="background:${PIE_COLORS[i%PIE_COLORS.length]}"></div><span>${l} (${data[i]})</span></div>`).join('');}
function renderLine(growth){const ctx=document.getElementById('linechart');if(!ctx)return;if(adminCharts.line)adminCharts.line.destroy();adminCharts.line=new Chart(ctx,{type:'line',data:{labels:growth.map(d=>d.date.slice(5)),datasets:[{label:'New players',data:growth.map(d=>d.count),borderColor:'#5aaa38',backgroundColor:'rgba(90,170,56,.12)',fill:true,tension:.35,pointRadius:3,pointBackgroundColor:'#5aaa38'}]},options:{responsive:true,maintainAspectRatio:false,scales:{y:{beginAtZero:true,ticks:{stepSize:1},grid:{color:'#f0f0f0'}},x:{grid:{display:false}}},plugins:{legend:{display:false}}}});}
let _ag=[];
async function loadAdminGames(){const[gr,tr]=await Promise.all([api('GET','/api/games'),api('GET','/api/tags')]);_ag=gr.data||[];allTags=tr.data||[];renderAdminGames(_ag);}
function renderAdminGames(games){document.getElementById('agtb').innerHTML=games.map(g=>`<tr><td>${g.coverImageUrl?`<img class="img-thumb" src="${g.coverImageUrl}" alt="">`:'<div class="img-thumb-empty">&#9823;</div>'}</td><td><strong>${g.title}</strong>${g.gameFileUrl?'<span class="file-badge">file</span>':'<span class="file-badge built-in">built-in</span>'}</td><td>${g.author}</td><td>${g.tags.map(t=>`<span class="gtag">${t}</span>`).join(' ')}</td><td><button class="btn-green bsm" onclick="openEditGame(${g.id})">Edit</button><button class="btn-red bsm" style="margin-left:4px" onclick="delGame(${g.id})">Delete</button></td></tr>`).join('');}
function asr_games(q){const lq=q.toLowerCase();renderAdminGames(_ag.filter(g=>g.title.toLowerCase().includes(lq)||g.author.toLowerCase().includes(lq)));}
function openAddGameModal(){agTags=[];['agtitle','agauth','agdur','agage'].forEach(id=>{document.getElementById(id).value='';});document.getElementById('agdesc').value='';document.getElementById('agrules').innerHTML='';document.getElementById('agfn').textContent='';document.getElementById('agimgprev').innerHTML='<img src="icons/image.svg" style="width:36px;opacity:.4" alt=""/>';renderTagSel('ag',agTags);openMod('m-addgame');}
async function addGame(){const title=v('agtitle');if(!title){toast('Title is required','err');return;}const body={title,author:v('agauth'),description:document.getElementById('agdesc').value.trim(),rules:document.getElementById('agrules').innerHTML,durationMinutes:parseInt(document.getElementById('agdur').value)||60,minimumAge:parseInt(document.getElementById('agage').value)||6,tags:agTags};const res=await api('POST','/api/admin/games',body);if(!res.success){toast(res.message||'Failed','err');return;}const gid=res.data.id;const cf=document.getElementById('agimg').files[0];if(cf){const fd=new FormData();fd.append('file',cf);await api('POST',`/api/admin/games/${gid}/cover`,fd,true);}const gf=document.getElementById('agfile').files[0];if(gf){const fd=new FormData();fd.append('file',gf);await api('POST',`/api/admin/games/${gid}/game-file`,fd,true);}closeMod('m-addgame');toast('Game added!');await loadAdminGames();}
async function openEditGame(id){const res=await api('GET',`/api/games/${id}`);const g=res.data;if(!g)return;document.getElementById('egidx').value=id;document.getElementById('egtitle').value=g.title;document.getElementById('egauth').value=g.author;document.getElementById('egdur').value=g.durationMinutes;document.getElementById('egage').value=g.minimumAge;document.getElementById('egdesc').value=g.description;document.getElementById('egrules').innerHTML=g.rules||'';const prev=document.getElementById('egimgprev');prev.innerHTML=g.coverImageUrl?`<img class="cover-prev" src="${g.coverImageUrl}" alt=""/>`:'<img src="icons/image.svg" style="width:36px;opacity:.4" alt=""/>';egTags=[...(g.tags||[])];renderTagSel('eg',egTags);openMod('m-editgame');}
async function saveEditGame(){const id=document.getElementById('egidx').value;const body={title:v('egtitle'),author:v('egauth'),description:document.getElementById('egdesc').value.trim(),rules:document.getElementById('egrules').innerHTML,durationMinutes:parseInt(document.getElementById('egdur').value)||60,minimumAge:parseInt(document.getElementById('egage').value)||6,tags:egTags};const res=await api('PUT',`/api/admin/games/${id}`,body);if(!res.success){toast(res.message||'Failed','err');return;}const cf=document.getElementById('egimg').files[0];if(cf){const fd=new FormData();fd.append('file',cf);await api('POST',`/api/admin/games/${id}/cover`,fd,true);}const gf=document.getElementById('egfile').files[0];if(gf){const fd=new FormData();fd.append('file',gf);await api('POST',`/api/admin/games/${id}/game-file`,fd,true);}closeMod('m-editgame');toast('Game updated!');await loadAdminGames();}
async function delGame(id){if(!confirm('Delete this game?'))return;await api('DELETE',`/api/admin/games/${id}`);toast('Deleted.','info');await loadAdminGames();}
function renderTagSel(prefix,selected){document.getElementById(prefix+'-tag-sel').innerHTML=allTags.map(t=>`<span class="tsel-pill ${selected.includes(t.name)?'on':''}" onclick="toggleTag('${prefix}','${t.name}')">${t.name}</span>`).join('');document.getElementById(prefix+'-selected-tags').innerHTML=selected.map(t=>`<span class="stag">${t}<span class="rx" onclick="removeTag('${prefix}','${t}')">x</span></span>`).join('');}
function toggleTag(p,name){const a=p==='ag'?agTags:egTags;a.includes(name)?a.splice(a.indexOf(name),1):a.push(name);renderTagSel(p,a);}
function removeTag(p,name){const a=p==='ag'?agTags:egTags;a.splice(a.indexOf(name),1);renderTagSel(p,a);}
function addCustomTag(p){const inp=document.getElementById(p+'-custom-tag');const val=inp.value.trim();if(!val)return;const a=p==='ag'?agTags:egTags;if(!a.includes(val))a.push(val);inp.value='';renderTagSel(p,a);}
let _au=[];
async function loadAdminUsers(){const res=await api('GET','/api/admin/users');_au=res.data||[];renderAdminUsers(_au);}
function renderAdminUsers(users){document.getElementById('autb').innerHTML=users.map(u=>`<tr><td><strong>${u.fullName}</strong><div style="font-size:.75rem;color:#888">@${u.username}</div></td><td><code style="font-size:.77rem">${u.uid}</code></td><td>${u.email}</td><td>${u.registeredAt}</td><td><span class="ustatus ${u.status==='ACTIVE'?'active':'banned'}">${u.status}</span></td><td><span class="online-dot ${u.isOnline?'on':'off'}"></span>${u.isOnline?'Online':'Offline'}</td><td>${u.status==='ACTIVE'?`<button class="btn-red bsm" onclick="banUser(${u.id})">Ban</button>`:`<button class="btn-green bsm" onclick="unbanUser(${u.id})">Unban</button>`}</td></tr>`).join('');}
function asr_users(q){const lq=q.toLowerCase();renderAdminUsers(_au.filter(u=>u.fullName.toLowerCase().includes(lq)||u.email.toLowerCase().includes(lq)||u.uid.toLowerCase().includes(lq)));}
async function banUser(id){await api('PUT',`/api/admin/users/${id}/ban`);toast('User banned.','info');await loadAdminUsers();}
async function unbanUser(id){await api('PUT',`/api/admin/users/${id}/unban`);toast('User unbanned.');await loadAdminUsers();}
async function loadAdminTags(){const res=await api('GET','/api/tags');allTags=res.data||[];document.getElementById('acattb').innerHTML=allTags.map(t=>`<tr><td><strong>${t.name}</strong></td><td>${t.description}</td><td><button class="btn-green bsm" onclick="openEditTag(${t.id},\`${t.name}\`,\`${t.description}\`)">Edit</button><button class="btn-red bsm" style="margin-left:4px" onclick="delTag(${t.id})">Delete</button></td></tr>`).join('');}
function openAddTagModal(){document.getElementById('cat-edit-id').value='';document.getElementById('ncn').value='';document.getElementById('ncdesc').value='';document.getElementById('cat-modal-title').textContent='Add New Tag';openMod('m-addcat');}
function openEditTag(id,name,desc){document.getElementById('cat-edit-id').value=id;document.getElementById('ncn').value=name;document.getElementById('ncdesc').value=desc;document.getElementById('cat-modal-title').textContent='Edit Tag';openMod('m-addcat');}
async function saveCat(){const id=document.getElementById('cat-edit-id').value,name=v('ncn'),desc=v('ncdesc');if(!name){toast('Name required','err');return;}const res=id?await api('PUT',`/api/tags/${id}`,{name,description:desc}):await api('POST','/api/tags',{name,description:desc});if(!res.success){toast(res.message||'Failed','err');return;}closeMod('m-addcat');toast(id?'Tag updated!':'Tag added!');await loadAdminTags();}
async function delTag(id){if(!confirm('Delete tag?'))return;await api('DELETE',`/api/tags/${id}`);toast('Deleted.','info');await loadAdminTags();}
async function adminChPw(){const o=v('acpold'),n=v('acpnew'),n2=v('acpnew2');if(n!==n2){setE('apwerr','Passwords do not match.');document.getElementById('apwerr').style.display='';return;}document.getElementById('apwerr').style.display='none';const res=await api('POST','/api/users/me/password',{oldPassword:o,newPassword:n,confirmPassword:n2});if(res.success){closeMod('m-admin-chpw');toast('Password changed!');}else toast(res.message||'Failed','err');}

/* ── Utilities ──────────────────────────────────────────────── */
function prevCover(input,prevId){const file=input.files[0];if(!file)return;const r=new FileReader();r.onload=e=>{document.getElementById(prevId).innerHTML=`<img class="cover-prev" src="${e.target.result}" alt=""/>`;};r.readAsDataURL(file);}
function fnChosen(input,labelId){const el=document.getElementById(labelId);if(el)el.textContent=input.files[0]?.name||'';}
function rtc(edId,cmd,val){document.getElementById(edId).focus();document.execCommand(cmd,false,val||null);}

/* ── Init ───────────────────────────────────────────────────── */
if (token && currentUser) startApp(); else go('s-login');
