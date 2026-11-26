'use strict';
const groupPage=document.querySelector('#app');
function groupToast(message){const el=document.querySelector('#toast');el.textContent=message;el.classList.add('visible');setTimeout(()=>el.classList.remove('visible'),4000);}
async function openGroups(){try{
 const response=await fetch('/api/config',{credentials:'omit'});if(!response.ok)throw new Error('加群页面暂时未连接，请稍后重试。');const config=await response.json();
 groupPage.innerHTML='<section class="panel public-groups"><div class="panel-head"><div class="eyebrow">SEE YOU IN THE CLUB</div><h1>社团伙伴，<em>在这里等你。</em></h1><p>已报名的同学可以直接加群，无需重复填写。每个群都需在 QQ 中分别确认加入。</p></div><div class="join-box">'+window.WELCOME_COMMUNITY.render(window.WELCOME_CONTENT,config)+'<p class="hint center">跳转不成功时，可保存对应二维码，在 QQ 扫一扫中从相册识别。是否成功加群，以 QQ 的提示为准。</p><p class="center"><a class="secondary" href="/">返回迎新首页</a></p></div></section>';
 groupPage.setAttribute('aria-busy','false');
 document.querySelector('#privacy-retention').textContent='在线资料保存 '+config.retentionDays+' 天。';document.querySelector('#privacy-contact').textContent='资料查询、更正或删除：'+config.privacyContact+'。';
}catch(error){groupPage.setAttribute('aria-busy','false');groupPage.innerHTML='<section class="panel center"><h1>社群页暂时没有连上。</h1><p>请检查网络后重试。</p><button class="primary" id="groups-retry">重新连接</button></section>';document.querySelector('#groups-retry').addEventListener('click',openGroups);}}
groupPage.addEventListener('click',async event=>{const button=event.target.closest('[data-copy-group]');if(!button)return;const response=await fetch('/api/config');if(!response.ok)return groupToast('暂时无法复制，请长按对应按钮复制链接。');const config=await response.json();const group=window.WELCOME_COMMUNITY.groups(window.WELCOME_CONTENT,config)[Number(button.dataset.copyGroup)];try{await navigator.clipboard.writeText(group.url);groupToast('邀请链接已复制。');}catch{groupToast('无法自动复制，请长按对应加群按钮复制链接。');}});
document.addEventListener('click',event=>{if(event.target.closest('a[href="#privacy"]')){event.preventDefault();document.querySelector('#privacy').showModal();}});
document.querySelector('#close-privacy').addEventListener('click',()=>document.querySelector('#privacy').close());
openGroups();
