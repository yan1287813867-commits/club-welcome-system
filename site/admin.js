'use strict';
const root = document.querySelector('#admin');
const interestNames = {code:'编程与开发',ai:'AI 与新技术',design:'设计与创意',game:'游戏与交互',hardware:'硬件与折腾',explore:'还在探索中'};
const pollNames = {project:'一起做一个小作品',workshop:'新技术体验课',share:'社团团建与趣味活动',challenge:'组队参加挑战'};
const esc = s => String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const selected = new Set();
let csrf='', page=1, query='', loading=false, mutating=false;
function toast(message) {
  const el=document.querySelector('#toast');
  el.textContent=message;el.classList.add('visible');
  clearTimeout(toast.timer);toast.timer=setTimeout(()=>el.classList.remove('visible'),4000);
}
async function api(path,options={}) {
  const response=await fetch(path,{...options,credentials:'same-origin',headers:{...(options.body?{'Content-Type':'application/json'}:{}),'X-CSRF-Token':csrf}});
  const data=await response.json();
  if(!response.ok){const error=new Error(data.error||'请求失败');error.status=response.status;throw error;}
  return data;
}
function login(message='') {
  csrf='';selected.clear();page=1;query='';
  root.innerHTML=`<section class="panel admin-login"><div class="eyebrow">FOR CLUB ORGANIZERS</div><h1>欢迎回来，<br><em>故事的组织者。</em></h1><p class="hint">登录后管理真实报名与活动提案。</p><form id="login"><label class="field">管理员账号<input name="username" required autocomplete="username" maxlength="80"></label><label class="field">管理员密码<input type="password" name="password" required autocomplete="current-password" maxlength="256"></label><p class="error" role="alert" id="login-error">${esc(message)}</p><button class="primary" type="submit">安全登录 ↗</button></form><p class="hint">本站没有默认密码。新设置的密码须8～64 位，包含大写字母、小写字母和数字。已有密码可继续登录。登录有效期 8 小时。</p></section>`;
}

function confirmDeletion(message) {
  const dialog=document.querySelector('#delete-confirm');
  document.querySelector('#delete-confirm-message').textContent=message;
  dialog.returnValue='';
  return new Promise(resolve=>{
    dialog.addEventListener('close',()=>resolve(dialog.returnValue==='delete'),{once:true});
    dialog.showModal();
  });
}

function chart(data,names) {
  const maximum=Math.max(1,...Object.values(data));
  return Object.entries(names).map(([key,label])=>`<div class="poll-row"><span>${label}</span><span>${data[key]||0}</span><progress max="${maximum}" value="${data[key]||0}" aria-label="${label}"></progress></div>`).join('');
}
function updateSelection() {
  const rows=[...root.querySelectorAll('[data-select-id]')];
  rows.forEach(el=>el.checked=selected.has(el.dataset.selectId));
  const all=root.querySelector('#select-page');
  const onPage=rows.filter(el=>el.checked).length;
  if(all){all.checked=rows.length>0&&onPage===rows.length;all.indeterminate=onPage>0&&onPage<rows.length;all.disabled=!rows.length||mutating;}
  const count=root.querySelector('#selection-count');
  if(count)count.textContent=`已选择 ${selected.size} 条（可跨页选择）`;
  const button=root.querySelector('#bulk-delete');
  if(button){button.disabled=!selected.size||mutating;button.textContent=`批量删除（${selected.size}）`;}
}
async function dashboard() {
  if(loading)return;loading=true;
  try {
    const data=await api(`/api/admin/signups?page=${page}&q=${encodeURIComponent(query)}`);
    if(page>1&&!data.items.length){page=Math.max(1,Math.ceil(data.total/30));loading=false;return dashboard();}
    const votes=Object.values(data.poll).reduce((a,b)=>a+b,0);
    root.innerHTML=`<div class="admin-heading"><div><div class="eyebrow">CLUB DESK / 招新工作台</div><h1>每一种好奇，<em>都在这里。</em></h1></div><button class="secondary" id="logout">退出登录</button></div>
    <div class="stats"><div class="stat"><strong>${data.allTotal}</strong><span>已保存的报名</span></div><div class="stat"><strong>${votes}</strong><span>活动提案投票</span></div><div class="stat"><strong>${data.total}</strong><span>当前筛选结果</span></div></div>
    <section class="admin-charts"><article><h2>兴趣分布 <small>多选</small></h2>${chart(data.interests,interestNames)}</article><article><h2>大家期待的活动</h2>${chart(data.poll,pollNames)}</article></section>
    <form class="admin-toolbar" id="search"><input name="q" value="${esc(query)}" maxlength="80" placeholder="搜索称呼、专业班级或编号" aria-label="搜索报名记录"><button class="primary" type="submit">搜索</button><button class="secondary" type="button" id="refresh">刷新</button><button class="secondary" type="button" id="export">导出当前筛选 CSV</button><button class="danger-button" type="button" id="bulk-delete" disabled>批量删除（0）</button></form>
    <p class="selection-note"><span id="selection-count" role="status">已选择 0 条（可跨页选择）</span><button class="small-link" id="clear-selection" type="button">清空选择</button><span>“全选”只选择当前页，最多 300 条；搜索会清空选择。</span></p>
    <p class="export-note">统计为全站实时数据，列表每页 30 条。时间按你的设备时区显示。网站未进行身份核验，同一人可能有多条报名。导出含 QQ 号，请勿公开分享。</p>
    <div class="table-wrap"><table><thead><tr><th class="selection-cell"><input type="checkbox" id="select-page" aria-label="全选本页"></th><th>编号 / 报名时间</th><th>称呼</th><th>专业 / 班级</th><th>QQ（选填）</th><th>兴趣</th><th>活动提案</th><th>操作</th></tr></thead><tbody>${data.items.length?data.items.map(row=>`<tr><td class="selection-cell"><input type="checkbox" data-select-id="${esc(row.id)}" aria-label="选择报名 ${esc(row.id)}"></td><td>${esc(row.id)}<br><small>${esc(new Date(row.created_at*1000).toLocaleString('zh-CN'))}</small></td><td>${esc(row.name)}</td><td>${esc(row.major)}</td><td>${esc(row.qq)||'—'}</td><td>${row.interests.map(key=>esc(interestNames[key])).join('、')}</td><td>${esc(pollNames[row.choice])||'尚未投票'}</td><td><button class="text-button" data-delete="${esc(row.id)}">删除</button></td></tr>`).join(''):'<tr><td colspan="8">暂无符合条件的报名记录。</td></tr>'}</tbody></table></div>
    <div class="pager"><button class="secondary" id="prev" ${page===1?'disabled':''}>上一页</button><span>第 ${page} / ${Math.max(1,Math.ceil(data.total/30))} 页</span><button class="secondary" id="next" ${page*30>=data.total?'disabled':''}>下一页</button></div>
    <p class="hint">删除会同时移除对应投票，不会删除未勾选记录。请先核对选中数量和报名编号，必要时先导出备份。数据库备份中的副本按备份制度清理。</p>`;
    updateSelection();
  } catch(error) {
    if(error.status===401)login('登录已过期，请重新登录。');
    else {
      toast(error.message);
      if(!root.querySelector('#refresh')) {
        root.innerHTML='<section class="panel"><h1>后台暂时无法读取。</h1><p id="dashboard-error" role="alert"></p><button class="primary" id="refresh">重新读取</button></section>';
        document.querySelector('#dashboard-error').textContent=error.message;
      }
    }
  } finally {loading=false;}
}
root.addEventListener('change',event=>{
  const checkbox=event.target;
  if(loading||mutating){updateSelection();return;}
  if(checkbox.id==='select-page') {
    for(const row of root.querySelectorAll('[data-select-id]')) {
      if(checkbox.checked){if(selected.size<300)selected.add(row.dataset.selectId);}
      else selected.delete(row.dataset.selectId);
    }
  } else if(checkbox.dataset.selectId) {
    if(checkbox.checked) {
      if(selected.size>=300){toast('每次最多选择 300 条。');updateSelection();return;}
      selected.add(checkbox.dataset.selectId);
    } else selected.delete(checkbox.dataset.selectId);
  }
  updateSelection();
});
root.addEventListener('submit',async event=>{
  event.preventDefault();if(mutating||loading)return;
  if(event.target.id==='search') {
    query=new FormData(event.target).get('q').trim();page=1;selected.clear();dashboard();
  }
  if(event.target.id==='login') {
    const button=event.target.querySelector('button');button.disabled=true;
    try {const data=await api('/api/admin/login',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(event.target)))});csrf=data.csrf;await dashboard();}
    catch(error){document.querySelector('#login-error').textContent=error.message;}
    finally{button.disabled=false;}
  }
});
root.addEventListener('click',async event=>{
  const button=event.target.closest('button');
  if(!button||button.disabled||loading||mutating)return;
  if(button.id==='clear-selection'){selected.clear();updateSelection();}
  if(button.id==='refresh')dashboard();
  if(button.id==='prev'){page--;dashboard();}
  if(button.id==='next'){page++;dashboard();}
  if(button.id==='logout'){try{await api('/api/admin/logout',{method:'POST',body:'{}'});login();}catch(error){toast(error.message);}}
  if(button.id==='bulk-delete') {
    const ids=[...selected];
    if(!ids.length)return;
    if(!await confirmDeletion(`确定删除已勾选的 ${ids.length} 条报名及其投票？\n此操作无法在后台撤销，未勾选记录不会删除。`))return;
    mutating=true;updateSelection();
    try {
      const data=await api('/api/admin/signups/bulk-delete',{method:'POST',body:JSON.stringify({ids})});
      selected.clear();await dashboard();toast(`已批量删除 ${data.deleted} 条报名及关联投票。`);
    } catch(error) {
      if(error.status===401)login();
      else if(error.status===409){selected.clear();await dashboard();toast(error.message);}
      else toast(error.message);
    } finally {mutating=false;updateSelection();}
  }
  if(button.dataset.delete) {
    if(!await confirmDeletion(`确定删除报名 ${button.dataset.delete} 及其投票？此操作不能在后台撤销。`))return;
    mutating=true;button.disabled=true;
    try {await api(`/api/admin/signups/${encodeURIComponent(button.dataset.delete)}`,{method:'DELETE'});selected.delete(button.dataset.delete);await dashboard();toast('报名和关联投票已删除。');}
    catch(error){if(error.status===401)login();else toast(error.message);}
    finally{mutating=false;button.disabled=false;updateSelection();}
  }
  if(button.id==='export') {
    button.disabled=true;
    try {
      const response=await fetch(`/api/admin/export?q=${encodeURIComponent(query)}`,{credentials:'same-origin'});
      if(!response.ok){const data=await response.json();if(response.status===401)login();throw new Error(data.error||'导出失败');}
      const url=URL.createObjectURL(await response.blob()),link=document.createElement('a');
      link.href=url;link.download=`社团报名-${new Date().toISOString().slice(0,10)}.csv`;link.click();
      setTimeout(()=>URL.revokeObjectURL(url),5000);toast('CSV 已生成，请在下载中查看。');
    }catch(error){toast(error.message);}finally{button.disabled=false;}
  }
});
api('/api/admin/session').then(data=>{csrf=data.csrf;dashboard();}).catch(error=>login(error.status===401?'':error.message));
