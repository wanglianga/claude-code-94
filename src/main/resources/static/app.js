// ============ 全局状态与 API ============
let ME = null;
let META = null;
const $ = (id) => document.getElementById(id);
const MAIN = () => $('main');

function esc(s) {
  if (s === null || s === undefined) return '';
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
function fmt(t) { return t ? t.replace('T', ' ').substring(0, 19) : '—'; }
function nowPlus(h, m = 0) {
  const d = new Date(Date.now() + h * 3600000 + m * 60000);
  d.setSeconds(0, 0);
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`;
}
function toast(msg, bad) {
  const t = $('toast');
  t.textContent = msg;
  t.style.background = bad ? '#c92a2a' : '#2b8a3e';
  t.style.opacity = '1';
  setTimeout(() => t.style.opacity = '0', 3200);
}

async function api(path, opts = {}) {
  const res = await fetch(path, {
    method: opts.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      [window.TOKEN_HEADER || 'X-Auth-Token']: localStorage.getItem('token') || ''
    },
    body: opts.body ? JSON.stringify(opts.body) : undefined
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || ('HTTP ' + res.status));
  return data;
}

const role = () => ME?.role;
const isAdmin = () => role() === 'ADMIN';
const can = (...roles) => isAdmin() || roles.includes(role());

// ============ 登录 ============
function fillLogin(u, p) { $('loginUser').value = u; $('loginPwd').value = p; }

async function doLogin() {
  try {
    const data = await api('/api/auth/login', { method: 'POST', body: { username: $('loginUser').value.trim(), password: $('loginPwd').value } });
    localStorage.setItem('token', data.token);
    ME = data;
    enterApp();
  } catch (e) { toast(e.message, true); }
}
async function doLogout() {
  try { await api('/api/auth/logout', { method: 'POST' }); } catch {}
  localStorage.removeItem('token');
  ME = null;
  $('appPage').style.display = 'none';
  $('loginPage').style.display = 'flex';
}

async function boot() {
  document.addEventListener('keydown', e => { if (e.key === 'Enter' && $('loginPage').style.display !== 'none') doLogin(); });
  const token = localStorage.getItem('token');
  if (!token) return;
  try {
    ME = await api('/api/auth/me');
    enterApp();
  } catch { localStorage.removeItem('token'); }
}

async function enterApp() {
  $('loginPage').style.display = 'none';
  $('appPage').style.display = 'block';
  $('userInfo').textContent = `${ME.displayName}｜${ME.roleLabel}`;
  $('navNew').style.display = can('CARRIER', 'DISPATCH') ? '' : 'none';
  META = await loadMeta();
  showBoard();
}

async function loadMeta() {
  const [carriers, customers, drivers, docks, enums] = await Promise.all([
    api('/api/meta/carriers'), api('/api/meta/customers'),
    api('/api/meta/drivers'), api('/api/meta/docks'), api('/api/meta/enums')
  ]);
  return { carriers, customers, drivers, docks, enums };
}

// ============ 调度看板 ============
async function showBoard() {
  const b = await api('/api/appointments/board');
  const stages = ['SCHEDULED','GATE','DOCK','QC','QUARANTINE','SETTLEMENT'];
  const done = (b.stageCounts?.DONE || 0);
  const cols = stages.map(s => {
    const list = b.vehicles.filter(v => v.stage === s);
    const labels = { SCHEDULED:'预约排窗', GATE:'闸口', DOCK:'月台', QC:'质检', QUARANTINE:'隔离', SETTLEMENT:'结算' };
    return `<div class="board-col"><h3>${labels[s]}<span>${list.length}</span></h3>` +
      list.map(v => cardHtml(v)).join('') + '</div>';
  }).join('');
  MAIN().innerHTML = `
    <div class="board-head">
      <div class="stat">在园/在途车次 <b>${b.vehicles.length}</b></div>
      <div class="stat">可用月台 <b>${b.usableDockCount}/${b.totalDockCount}</b></div>
      <div class="stat">已完成 <b>${done}</b></div>
      <div class="stat">当前时段 <b>${b.nightNow ? '夜间(排队)' : '白天'}</b></div>
      ${b.powerShedding ? `<div class="power-alert">⚡ 园区限电中：${esc(b.powerNote)}</div>` : ''}
      ${can('DISPATCH') ? `
        <button class="${b.powerShedding ? 'ok' : 'warn'}" onclick="togglePower(${b.powerShedding})">
          ${b.powerShedding ? '解除限电' : '启动园区限电'}</button>` : ''}
    </div>
    <div class="board-cols">${cols}</div>`;
}

async function togglePower(shedding) {
  const note = shedding ? '电力恢复，月台全开' : '接到供电通知 14:00-17:00 限电，冷库月台减半运行';
  await api('/api/appointments/power-shedding', { method: 'POST', body: { shedding: !shedding, note } });
  toast(shedding ? '限电已解除' : '已发布限电通知，相关车次自动挂限电异常');
  showBoard();
}

function badge(v) {
  const map = {};
  ['PENDING','SCHEDULED','LICENSE_HOLD','QUEUED_NIGHT','GATE_REJECTED','AT_GATE','IN_PARK','AT_DOCK',
   'UNLOADED','IN_QC','QUARANTINED','RELEASED','DOWNGRADED','REJECTED','SETTLED','CLOSED'].forEach((k,i)=>{});
  const cls = v.status === 'GATE_REJECTED' || v.status === 'REJECTED' ? 'red'
    : v.status === 'QUARANTINED' ? 'orange'
    : v.status === 'RELEASED' || v.status === 'DOWNGRADED' ? 'green'
    : v.status === 'SETTLED' ? 'gray'
    : v.status === 'LICENSE_HOLD' || v.status === 'QUEUED_NIGHT' ? 'purple' : 'blue';
  const ex = v.openExceptionCount > 0 ? ` <span class="badge red">异常${v.openExceptionCount}</span>` : '';
  return `<span class="badge ${cls}">${esc(v.statusLabel)}</span>${ex}`;
}

function cardHtml(v) {
  return `<div class="card" onclick="showDetail(${v.id})">
    <div class="code">${esc(v.code)}</div>
    <div class="plate">${esc(v.plateNo)} ${v.urgent ? '<span class="badge red">加急</span>' : ''}</div>
    <div class="meta">${esc(v.carrierName)}｜${esc(v.driverName)}</div>
    <div class="meta">${esc(v.requiredZone === 'FROZEN' ? '冷冻' : v.requiredZone === 'CHILLED' ? '冷藏' : v.requiredZone === 'CONST' ? '恒温' : '?')}
      ｜月台 ${esc(v.dockCode || '—')}｜窗口 ${fmt(v.windowStart)}</div>
    <div style="margin-top:5px">${badge(v)}</div>
  </div>`;
}

// ============ 车次列表 ============
async function showList() {
  const list = await api('/api/appointments');
  MAIN().innerHTML = `<div class="panel"><h2>车次列表（当前角色可见范围）</h2>
    <table class="data"><tr><th>单号</th><th>车牌</th><th>承运商</th><th>货主</th><th>温区</th>
      <th>窗口</th><th>月台</th><th>状态</th><th>异常</th></tr>` +
    list.map(v => `<tr style="cursor:pointer" onclick="showDetail(${v.id})">
      <td>${esc(v.code)}</td><td><b>${esc(v.plateNo)}</b>${v.urgent?' 加急':''}</td>
      <td>${esc(v.carrierName)}</td><td>${(v.customers||[]).map(esc).join('、')}</td>
      <td>${esc(v.requiredZone)}</td><td>${fmt(v.windowStart)}</td><td>${esc(v.dockCode||'—')}</td>
      <td>${badge(v)}</td><td>${v.openExceptionCount}</td></tr>`).join('') + '</table></div>';
}

// ============ 提交预约表单 ============
function showNewForm() {
  const carrierOpts = META.carriers.map(c => `<option value="${c.id}">${esc(c.name)}（服务分${c.serviceScore}）</option>`).join('');
  const custOpts = META.customers.map(c => `<option value="${c.id}">${esc(c.name)}（${esc(c.priorityLabel)}）</option>`).join('');
  MAIN().innerHTML = `<div class="panel"><h2>提交车辆预约</h2>
    <div class="row2">
      <div><label>承运商</label><select id="f_carrier" ${role()==='CARRIER'?'disabled':''}>${carrierOpts}</select></div>
      <div><label>司机（注意证件有效期）</label><select id="f_driver"></select></div>
    </div>
    <div class="row3">
      <div><label>车牌</label><input id="f_plate" value="鲁B${Math.floor(10000+Math.random()*89999)}"></div>
      <div><label>温度设备号</label><input id="f_device" value="T-DEV-${Math.floor(1000+Math.random()*8999)}"></div>
      <div><label>电子封签号</label><input id="f_seal" value="SEAL-${Math.floor(100000+Math.random()*899999)}"></div>
    </div>
    <label>货品总体描述</label><input id="f_desc" value="冷链混装货物">
    <div class="row2">
      <div><label>期望到园时间</label><input id="f_time" value="${nowPlus(2)}"></div>
      <div style="display:flex;align-items:center;gap:8px;margin-top:18px">
        <input type="checkbox" id="f_urgent" style="width:auto"><label for="f_urgent" style="margin:0">客户临时加急（最高优先级插单）</label>
      </div>
    </div>
    <h2 style="margin-top:16px">货物明细（可多货主混装）</h2>
    <div id="lines"></div>
    <button onclick="addLine()" class="ghost">+ 添加货主货物行</button>
    <div class="actions"><button class="primary big" style="max-width:240px" onclick="submitAppt()">提交预约并自动排窗</button></div>
  </div>`;
  $('f_carrier').value = role() === 'CARRIER' ? ME.bindCarrierId : META.carriers[0].id;
  refreshDrivers();
  $('f_carrier').addEventListener('change', refreshDrivers);
  addLine();
}

function refreshDrivers() {
  const cid = Number($('f_carrier').value);
  $('f_driver').innerHTML = META.drivers.filter(d => d.carrierId === cid).map(d => {
    const expired = d.licenseExpiry && d.licenseExpiry.substring(0,10) <= new Date().toISOString().substring(0,10);
    return `<option value="${d.id}">${esc(d.name)} 驾驶证至${d.licenseExpiry}${expired?' ⚠已过期':''}</option>`;
  }).join('');
}

let lineSeq = 0;
function addLine() {
  const seq = ++lineSeq;
  const custOpts = META.customers.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
  const div = document.createElement('div');
  div.className = 'row3';
  div.style.borderTop = '1px dashed #c3cfdd';
  div.style.paddingTop = '8px';
  div.id = 'line_' + seq;
  div.innerHTML = `
    <div><label>货主</label><select class="l_cust">${custOpts}</select></div>
    <div><label>货品</label><input class="l_goods" value="冷冻调理包"></div>
    <div><label>温区</label><select class="l_zone">
      <option value="FROZEN">冷冻(-18℃)</option><option value="CHILLED">冷藏(0~4℃)</option><option value="CONST">恒温(8~15℃)</option>
    </select></div>
    <div><label>件数</label><input type="number" class="l_pieces" value="100"></div>
    <div><label>目标仓/库位</label><input class="l_wh" value="1号冷库-A区"></div>
    <div style="align-self:end"><button class="danger" onclick="document.getElementById('line_${seq}').remove()">删除行</button></div>`;
  $('lines').appendChild(div);
}

async function submitAppt() {
  const cid = role() === 'CARRIER' ? ME.bindCarrierId : Number($('f_carrier').value);
  const lines = [...document.querySelectorAll('#lines > div')].map(d => ({
    customerId: Number(d.querySelector('.l_cust').value),
    goodsName: d.querySelector('.l_goods').value,
    zone: d.querySelector('.l_zone').value,
    pieces: Number(d.querySelector('.l_pieces').value),
    targetWarehouse: d.querySelector('.l_wh').value
  }));
  const body = {
    carrierId: cid, driverId: Number($('f_driver').value),
    plateNo: $('f_plate').value.trim(), deviceNo: $('f_device').value.trim(), sealNo: $('f_seal').value.trim(),
    cargoTypeDesc: $('f_desc').value, estimatedPieces: lines.reduce((s,l)=>s+l.pieces,0),
    requestedTime: $('f_time').value.replace('T',' '), urgent: $('f_urgent').checked, lines
  };
  try {
    const a = await api('/api/appointments', { method: 'POST', body });
    toast('预约已提交并完成排窗：' + a.code);
    showDetail(a.id);
  } catch (e) { toast(e.message, true); }
}

// ============ 车次详情 ============
async function showDetail(id) {
  const a = await api('/api/appointments/' + id);
  window.__cur = a;
  const isDriverExpired = a.status === 'LICENSE_HOLD';
  const kv = (k, v) => `<div><b>${k}</b>${esc(v ?? '—')}</div>`;

  const actionsHtml = buildActions(a);

  const cargoRows = a.cargoLines.map(l => `<tr>
      <td>${esc(l.customerName)}</td><td>${esc(l.goodsName)}</td><td>${esc(l.zoneLabel)}</td>
      <td>${l.pieces}</td><td>${esc(l.targetWarehouse||'—')}${l.changedWarehouse?' → <b style=\'color:#d9480f\'>'+esc(l.changedWarehouse)+'</b>':''}</td>
      <td>${l.lineDecisionLabel ? '<span class="badge '+(l.lineDecision==='REJECT'?'red':l.lineDecision==='RELEASE'?'green':'orange')+'">'+esc(l.lineDecisionLabel)+'</span>' : '待处置'}</td>
    </tr>`).join('');

  MAIN().innerHTML = `
    <span class="backlink" onclick="showBoard()">← 返回看板</span>
    <div class="layout">
      <div>
        <div class="panel">
          <h2>${esc(a.code)}｜${esc(a.plateNo)} ${badge(a)} ${a.urgent?'<span class="badge red">加急</span>':''}</h2>
          <div class="kv">
            ${kv('承运商', a.carrierName)}${kv('司机', a.driverName)}${kv('温区', a.requiredZone)}
            ${kv('温度设备', a.deviceNo)}${kv('电子封签', a.sealNo)}${kv('月台', a.dockCode)}
            ${kv('期望到园', fmt(a.requestedTime))}${kv('入场窗口', fmt(a.windowStart)+' ~ '+fmt(a.windowEnd))}${kv('到园时间', fmt(a.gateArriveTime))}
            ${kv('靠台', fmt(a.dockStartTime))}${kv('卸完', fmt(a.dockEndTime))}
            ${a.unloadDurationMinutes!==undefined?kv('卸货时长', a.unloadDurationMinutes+' 分钟'):''}
            ${kv('开门前温度', a.preOpenTempC!==null && a.preOpenTempC!==undefined ? a.preOpenTempC+'℃' : '—')}
            ${kv('破损件数', a.damagedPieces)}${kv('质检结果', a.qcResultLabel)}
            ${kv('抽样/化冻', (a.sampledPieces??'—')+' / '+(a.thawedPieces??'—'))}
            ${kv('签收件数', a.signedPieces)}${kv('预计放行', fmt(a.estimatedReleaseTime))}
          </div>
          <div class="section-flag">排程说明：${esc(a.scheduleNote)}</div>
          ${a.gateNote ? `<div class="section-flag">闸口备注：${esc(a.gateNote)}</div>` : ''}
          <div class="actions">${actionsHtml}</div>
        </div>

        <div class="panel">
          <h2>货物（多货主混装，可逐货主处置）</h2>
          <table class="data"><tr><th>货主</th><th>货品</th><th>温区</th><th>件数</th><th>目标仓</th><th>处置</th></tr>${cargoRows}</table>
        </div>

        <div class="panel">
          <h2>温控设备原始数据（共 ${a.readings.length} 点）</h2>
          ${tempChart(a)}
          <div class="actions" style="margin-top:10px">
            ${can('CARRIER','DISPATCH') && ['SCHEDULED','QUEUED_NIGHT','IN_PARK','AT_GATE','AT_DOCK'].includes(a.status) ? `
              <button onclick="uploadReadings('normal')">回传正常曲线(8点)</button>
              <button class="warn" onclick="uploadReadings('gap')">回传断点曲线</button>
              <button class="danger" onclick="uploadReadings('hot')">回传失温曲线(超温+断点)</button>` : ''}
          </div>
        </div>

        <div class="panel">
          <h2>异常协同（承运商/调度/质检/客服/结算 同车处理）</h2>
          ${a.exceptions.length === 0 ? '<div class="section-flag">暂无异常</div>' : a.exceptions.map(exHtml).join('')}
        </div>

        ${a.settlement ? settlementHtml(a) : ''}
      </div>

      <div>
        ${can('CS','DISPATCH','SETTLEMENT') ? await csPanel(a) : ''}
        <div class="panel">
          <h2>同车协同沟通</h2>
          <textarea id="cmt" placeholder="按当前角色在本车次留言（所有角色可见）"></textarea>
          <button class="primary" onclick="postComment()">留言</button>
        </div>
        <div class="panel">
          <h2>车次时间线（全环节轨迹）</h2>
          ${a.timeline.map(t => `<div class="timeline-item">
              <div class="t-time">${fmt(t.time)}｜${esc(t.actor)}</div>
              <div class="t-action">${esc(t.action)}</div>
              <div class="t-detail">${esc(t.detail)}</div></div>`).join('')}
        </div>
      </div>
    </div>`;
}

function buildActions(a) {
  const btns = [];
  const driverOpts = META.drivers.filter(d => d.carrierId === a.carrierId)
    .map(d => `<option value="${d.id}">${esc(d.name)}</option>`).join('');

  if (a.status === 'LICENSE_HOLD' && can('DISPATCH','CARRIER')) {
    btns.push(`<span style="display:flex;gap:6px;align-items:center">
      <select id="repDriver" style="width:auto;margin:0">${driverOpts}</select>
      <button class="primary" onclick="replaceDriver()">更换有效司机并重新排窗</button></span>`);
  }
  if (['SCHEDULED'].includes(a.status) && can('CS','DISPATCH')) {
    btns.push(`<button class="danger" onclick="markUrgent()">客户临时加急·重排</button>`);
  }
  if (['SCHEDULED','QUEUED_NIGHT'].includes(a.status) && can('DISPATCH')) {
    btns.push(`<button onclick="rescheduleNow()">调度改约到当前</button>`);
  }
  if (a.status === 'SCHEDULED' && can('DISPATCH')) {
    btns.push(`<button class="ok" onclick="gateCheck(false,false)">闸口核验通过</button>`);
    btns.push(`<button class="warn" onclick="gateCheck(true,false)">晚到核验</button>`);
    btns.push(`<button class="danger" onclick="gateCheck(false,true)">封签不符拦截</button>`);
  }
  if (a.status === 'GATE_REJECTED' && can('DISPATCH')) {
    btns.push(`<button class="primary" onclick="gateOverride()">调度复核·人工放行</button>`);
  }
  if (a.status === 'QUEUED_NIGHT' && can('DISPATCH')) {
    btns.push(`<button class="ok" onclick="releaseQueue()">月台空闲·结束夜间排队</button>`);
  }
  if (a.status === 'IN_PARK' && can('DISPATCH')) {
    btns.push(`<button class="ok" onclick="dockStart()">月台接车·开始卸货</button>`);
  }
  if (a.status === 'AT_DOCK' && can('DISPATCH')) {
    btns.push(`<button class="ok" onclick="unload(0)">卸货完成(温度正常/0破损)</button>`);
    btns.push(`<button class="warn" onclick="unload(3)">卸货完成(3件破损)</button>`);
    btns.push(`<button class="danger" onclick="unloadHot()">卸货完成(开门前已失温)</button>`);
  }
  if (a.status === 'UNLOADED' && can('QC')) {
    btns.push(`<button class="ok" onclick="submitQc('PASS')">质检合格·放行</button>`);
    btns.push(`<button class="warn" onclick="submitQc('THAW')">质检发现化冻·隔离</button>`);
    btns.push(`<button class="danger" onclick="submitQc('REJECT')">严重不合格·建议拒收</button>`);
  }
  if (a.status === 'QUARANTINED' && can('DISPATCH','QC')) {
    btns.push(`<button class="ok" onclick="makeDecision('RELEASE')">处置:复核放行</button>`);
    btns.push(`<button class="warn" onclick="makeDecision('DOWNGRADE')">处置:降级入库</button>`);
    btns.push(`<button class="danger" onclick="makeDecision('REJECT')">处置:拒收</button>`);
  }
  if (['RELEASED','DOWNGRADED','REJECTED'].includes(a.status) && can('SETTLEMENT')) {
    btns.push(`<button class="primary" onclick="settle('FULL')">登记全数签收并结算</button>`);
    btns.push(`<button onclick="settle('PARTIAL')">短少部分签收并结算</button>`);
    btns.push(`<button class="danger" onclick="settle('REFUSED')">拒签并结算</button>`);
  }
  if (can('CS','DISPATCH') && a.cargoLines.length) {
    const lineOpts = a.cargoLines.map(l => `<option value="${l.id}">${esc(l.customerName)}·${esc(l.goodsName)}</option>`).join('');
    btns.push(`<span style="display:flex;gap:6px;align-items:center">
      <select id="whLine" style="width:auto;margin:0">${lineOpts}</select>
      <input id="whTo" style="width:130px;margin:0" placeholder="新库位" value="临时3号冷库">
      <button onclick="changeWarehouse()">货主临时改仓</button></span>`);
  }
  return btns.join(' ') || '<span class="section-flag">当前状态/角色暂无操作按钮（可在下方留言协同）</span>';
}

function exHtml(e) {
  return `<div class="ex-item ${e.status==='CLOSED'?'handled':''}">
    <div class="ex-head"><span>${e.status==='CLOSED'?'✅':'⚠'} ${esc(e.typeLabel)} <span class="badge ${e.status==='CLOSED'?'green':'red'}">${esc(e.statusLabel)}</span></span>
      <span class="meta">严重度 ${'★'.repeat(e.severity)}</span></div>
    <div class="meta">${esc(e.detail)}</div>
    <div class="meta">发现：${esc(e.raisedBy)} @ ${fmt(e.raisedAt)}｜建议责任：${esc(e.responsiblePartyLabel)}</div>
    ${e.resolution ? `<div class="meta">处理结论：${esc(e.resolution)}</div>` : ''}
    ${can('DISPATCH','QC','CS','SETTLEMENT') && e.status !== 'CLOSED' ? `
      <div class="row2" style="margin-top:6px">
        <input id="res_${e.id}" placeholder="处理措施/结论">
        <select id="party_${e.id}" style="margin:4px 0 10px">
          ${['CARRIER','PARK','CUSTOMER','NONE'].map(p =>
            `<option value="${p}" ${p===e.responsibleParty?'selected':''}}>${ {CARRIER:'承运商',PARK:'园区',CUSTOMER:'货主/客户',NONE:'无责'}[p] }</option>`).join('')}
        </select>
      </div>
      <div class="actions">
        <button onclick="handleEx(${e.id},false)">协同处理</button>
        <button class="ok" onclick="handleEx(${e.id},true)">判定并关闭异常</button>
      </div>` : ''}
  </div>`;
}

function settlementHtml(a) {
  const s = a.settlement;
  return `<div class="panel"><h2>结算单 ${esc(s.code)}</h2>
    <div class="kv" style="grid-template-columns:repeat(2,1fr)">
      <div><b>责任结构</b>${esc(s.responsibilitySummary)}</div>
      <div><b>最终处置</b>${esc(a.decisionLabel)}</div>
      <div><b>运费基数</b>¥${s.freightBase}</div>
      <div><b>承运商扣罚</b><span style="color:#c92a2a">¥${s.carrierPenalty}</span></div>
      <div><b>实付承运商</b>¥${s.carrierPayable}</div>
      <div><b>客户赔付</b><span style="color:#d9480f">¥${s.customerClaim}</span></div>
      <div><b>园区补偿</b>¥${s.parkCompensation}</div>
      <div><b>服务分变动</b>${s.scoreDelta}（当前 ${a.serviceScore}）</div>
    </div>
    <div class="section-flag">${esc(s.note)}</div></div>`;
}

async function csPanel(a) {
  try {
    const x = await api(`/api/customer/appointments/${a.id}/explain`);
    return `<div class="panel"><h2>客户客服口径（责任/货损/放行时间）</h2>
      <div class="script-box">${esc(x.script)}</div>
      <table class="data" style="margin-top:10px">
        <tr><th>责任方</th><th>比例</th><th>依据（客服话术）</th></tr>
        ${x.responsibilityDetail.map(r => `<tr><td>${esc(r.partyLabel)}</td><td><b>${r.percent}%</b></td>
          <td>${r.why.map(esc).join('；')}</td></tr>`).join('')}
      </table>
      <div class="kv" style="margin-top:8px">
        <div><b>化冻/破损</b>${x.cargoLoss.thawedPieces}/${x.cargoLoss.damagedPieces} 件</div>
        <div><b>预估货损</b>¥${x.cargoLoss.estimatedAmount}</div>
        <div><b>放行</b>${esc(x.releaseText)}</div>
      </div>
      ${x.customerClaim !== undefined ? `<div class="kv" style="margin-top:6px">
        <div><b>实际客户赔付</b>¥${x.customerClaim}</div><div><b>园区补偿</b>¥${x.parkCompensation}</div></div>` : ''}
      <div class="actions"><button onclick="showTrace(${a.id})">查看批次运输质量追溯（原始温度/卸货/签收）</button></div>
    </div>`;
  } catch (e) { return ''; }
}

async function showTrace(id) {
  const x = await api(`/api/customer/appointments/${id}/trace`);
  const rows = x.rawReadings.map(r => `<tr><td>${fmt(r.time)}</td><td>${r.tempC}℃</td><td>${esc(r.phase)}</td></tr>`).join('');
  MAIN().innerHTML = `<span class="backlink" onclick="showDetail(${id})">← 返回车次</span>
    <div class="panel"><h2>批次运输质量追溯 · ${esc(x.code)} / ${esc(x.plateNo)}</h2>
      <div class="kv">
        <div><b>承运商</b>${esc(x.carrierName)}</div><div><b>设备</b>${esc(x.deviceNo)}</div><div><b>封签</b>${esc(x.sealNo)}</div>
        <div><b>窗口</b>${fmt(x.windowStart)}</div><div><b>到园</b>${fmt(x.gateArriveTime)}</div>
        <div><b>卸货时长</b>${x.unloadDurationMinutes??'—'} 分钟</div>
        <div><b>开门前温度</b>${x.preOpenTempC??'—'}℃</div><div><b>质检</b>${esc(x.qcResultLabel||'—')}</div>
        <div><b>处置</b>${esc(x.decisionLabel||'—')}</div><div><b>签收</b>${esc(x.signResultLabel||'—')} ${x.signedPieces??''}件</div>
        <div><b>破损</b>${x.damagedPieces??0} 件</div><div><b>采样点</b>${x.readingCount}</div>
      </div>
      <div class="section-flag">曲线断点 ${x.analysis.gapCount} 处，超温点 ${x.analysis.excursionCount} 个，温度区间 ${x.analysis.minC??'—'}℃ ~ ${x.analysis.maxC??'—'}℃</div>
      <div class="section-flag">卸货照片：${esc(x.unloadPhotos||'—')}</div>
    </div>
    <div class="panel"><h2>温控设备原始数据（不可篡改留存）</h2>
      <table class="data"><tr><th>时间</th><th>温度</th><th>阶段</th></tr>${rows}</table></div>
    <div class="panel"><h2>完整时间线</h2>
      ${x.timeline.map(t => `<div class="timeline-item"><div class="t-time">${fmt(t.time)}｜${esc(t.actor)}</div>
        <div class="t-action">${esc(t.action)}</div><div class="t-detail">${esc(t.detail)}</div></div>`).join('')}</div>`;
}

// ============ SVG 温度曲线 ============
function tempChart(a) {
  if (!a.readings.length) return '<div class="section-flag">暂无采样数据</div>';
  const rs = a.readings;
  const W = 640, H = 160, pad = 34;
  const temps = rs.map(r => r.tempC);
  let mn = Math.min(...temps, a.requiredZone === 'FROZEN' ? -25 : 0);
  let mx = Math.max(...temps, 10);
  if (mx - mn < 10) { mx += 5; mn -= 5; }
  const x = i => pad + i * (W - 2*pad) / Math.max(1, rs.length-1);
  const y = t => H - pad - (t - mn) * (H - 2*pad) / (mx - mn);
  const path = rs.map((r,i) => (i?'L':'M') + x(i).toFixed(1) + ',' + y(r.tempC).toFixed(1)).join(' ');
  const alarm = { FROZEN: -15, CHILLED: 7, CONST: 15 }[a.requiredZone];
  const alarmY = alarm !== undefined && alarm <= mx && alarm >= mn ? y(alarm) : null;
  const pts = rs.map((r,i) => `<circle cx="${x(i).toFixed(1)}" cy="${y(r.tempC).toFixed(1)}" r="3"
    fill="${r.tempC > (alarm??999) ? '#c92a2a' : '#1d6fe0'}"/>`).join('');
  return `<svg viewBox="0 0 ${W} ${H}" style="width:100%;max-width:720px;background:#f8fbff;border-radius:8px">
    <line x1="${pad}" y1="${H-pad}" x2="${W-pad}" y2="${H-pad}" stroke="#9fb4cc"/>
    <line x1="${pad}" y1="${pad}" x2="${pad}" y2="${H-pad}" stroke="#9fb4cc"/>
    ${alarmY !== null ? `<line x1="${pad}" y1="${alarmY}" x2="${W-pad}" y2="${alarmY}" stroke="#e8590c" stroke-dasharray="5,4"/>
      <text x="${W-pad-70}" y="${alarmY-4}" fill="#e8590c" font-size="11">报警线 ${alarm}℃</text>` : ''}
    <path d="${path}" fill="none" stroke="#1d6fe0" stroke-width="2"/>${pts}
    <text x="${pad}" y="16" fill="#5b6b7f" font-size="11">${mn.toFixed(0)}℃ ~ ${mx.toFixed(0)}℃｜${rs.length} 点｜采样间隔异常以断点体现在折线中</text>
  </svg>`;
}

// ============ 操作接口封装 ============
async function act(path, body, okMsg, method='POST') {
  try { await api(path, { method, body }); toast(okMsg); const id = window.__cur?.id; if (id) showDetail(id); }
  catch (e) { toast(e.message, true); }
}
function replaceDriver() { act(`/api/appointments/${cur().id}/replace-driver`, { driverId: Number($('repDriver').value) }, '已更换司机并重新排窗'); }
function markUrgent() { act(`/api/appointments/${cur().id}/urgent`, {}, '已按临时加急重新排窗'); }
function rescheduleNow() { act(`/api/appointments/${cur().id}/reschedule`, { fromTime: nowPlus(0) }, '已改约到当前时段'); }
function gateCheck(late, badSeal) {
  const a = cur();
  const arrival = late ? nowPlus(0, 45) : nowPlus(0);
  act(`/api/appointments/${a.id}/gate-check`, {
    plateScan: a.plateNo, sealChecked: badSeal ? 'SEAL-WRONG-000' : a.sealNo, arrivalTime: arrival
  }, badSeal ? '封签不符，闸口已拦截' : '闸口核验完成');
}
function gateOverride() { act(`/api/appointments/${cur().id}/gate-override`, { detail: '调度现场复核封签系标签污损，扫码设备重新确认一致，人工放行' }, '已人工放行'); }
function releaseQueue() { act(`/api/appointments/${cur().id}/release-queue`, {}, '已结束夜间排队'); }
function dockStart() { act(`/api/appointments/${cur().id}/dock-start`, { time: nowPlus(0) }, '已靠台开始卸货'); }
function unload(damaged) {
  const a = cur();
  const temp = a.requiredZone === 'FROZEN' ? -18 : a.requiredZone === 'CHILLED' ? 3 : 11;
  act(`/api/appointments/${a.id}/unload`, {
    preOpenTempC: temp, photos: 'photos://unload/'+a.code+'/door-open.jpg;pallet-1.jpg;pallet-2.jpg',
    damagedPieces: damaged, endTime: nowPlus(0, 35)
  }, '卸货回执已上传');
}
function unloadHot() {
  act(`/api/appointments/${cur().id}/unload`, {
    preOpenTempC: -6, photos: 'photos://unload/hot-case.jpg', damagedPieces: 5, endTime: nowPlus(0, 40)
  }, '开门前温度异常已登记');
}
function submitQc(result) {
  const body = result === 'PASS' ? { result, sampledPieces: 20, thawedPieces: 0, note: '感官与中心温度均合格' }
    : result === 'THAW' ? { result, sampledPieces: 20, thawedPieces: 12, note: '外包装有水渍，虾仁软化，判定化冻' }
    : { result, sampledPieces: 20, thawedPieces: 18, note: '整批软化伴异味，建议拒收' };
  act(`/api/appointments/${cur().id}/qc`, body, '质检结果已提交');
}
function makeDecision(d) {
  const a = cur();
  // 化冻场景演示逐货主差异处置：冷冻货降级/拒收，冷藏货放行（若非整车拒收）
  let lines;
  if (d === 'DOWNGRADE') lines = a.cargoLines.map(l => ({ lineId: l.id, decision: l.zone === 'FROZEN' ? 'DOWNGRADE' : 'RELEASE' }));
  else lines = a.cargoLines.map(l => ({ lineId: l.id, decision: d }));
  act(`/api/appointments/${a.id}/decision`, { decision: d, lines, note: '调度、质检、客服、结算现场会签判定' }, '处置结论已记录');
}
function changeWarehouse() {
  act(`/api/appointments/${cur().id}/warehouse-change`,
    { lineId: Number($('whLine').value), newWarehouse: $('whTo').value }, '已登记货主临时改仓');
}
function settle(sign) {
  const a = cur();
  const full = (a.cargoLines||[]).reduce((s,l)=>s+l.pieces,0);
  const signed = sign === 'FULL' ? full : sign === 'PARTIAL' ? Math.max(0, full-10) : 0;
  act(`/api/appointments/${a.id}/settle`, { signResult: sign, signedPieces: signed, note: '按处置结论与签收结果结案' }, '结算单已生成');
}
function handleEx(exId, close) {
  act(`/api/appointments/exceptions/${exId}/handle`,
    { resolution: $('res_'+exId).value || '相关角色已现场核实并落实整改', party: $('party_'+exId).value, close },
    close ? '异常已判定关闭' : '已记录处理进展');
}
function postComment() {
  const v = $('cmt').value.trim();
  if (!v) return toast('请填写留言内容', true);
  act(`/api/appointments/${cur().id}/comments`, { detail: v }, '留言已同步到车次时间线');
}

async function uploadReadings(kind) {
  const a = cur();
  const base = a.requiredZone === 'FROZEN' ? -18 : a.requiredZone === 'CHILLED' ? 3 : 11;
  const list = [];
  for (let i = 7; i >= 0; i--) {
    let t = base + Math.sin(i) * 0.8;
    let gap = 0;
    if (kind === 'gap' && i === 4) gap = 45;             // 制造 >20 分钟断点
    if (kind === 'hot' && i <= 3) t = base + 14 + i;     // 后段失温
    if (kind === 'hot' && i === 5) gap = 50;
    list.push({ time: nowPlus(0, -i * 12 - gap), tempC: Math.round(t*10)/10,
      phase: i === 0 ? '到园' : '运输途中' });
  }
  // 时间排序后断点间隔才成立：重排为升序
  list.sort((x,y) => x.time.localeCompare(y.time));
  try {
    await api(`/api/appointments/${a.id}/readings`, { readings: list });
    toast('温控原始数据已回传并自动分析断点/超温');
    showDetail(a.id);
  } catch (e) { toast(e.message, true); }
}

function cur() { return window.__cur; }
boot();
