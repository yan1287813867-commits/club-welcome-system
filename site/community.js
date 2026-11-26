'use strict';
window.WELCOME_COMMUNITY = (()=>{
const esc = value => String(value ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function availableGroups(content,config){
  const configured=Array.isArray(content.qqGroups)&&content.qqGroups.length?content.qqGroups:[{name:content.groupLabel,url:config.qqGroupUrl}];
  return configured.map((group,index)=>({...group,url:group.url||(index===0?config.qqGroupUrl:'')})).filter(group=>{
    try{const url=new URL(group.url);return url.protocol==='https:'&&url.hostname==='qm.qq.com';}catch{return false;}
  });
}
function renderGroups(content,config){return '<div class="community-groups">'+availableGroups(content,config).map((group,index)=>{
  const qr=['/assets/qq-recruitment.png','/assets/qq-association.png'].includes(group.qrSrc)?'<figure class="community-qr"><div class="qr-frame"><span class="qr-frame-label">CLUB INVITATION</span><div class="qr-paper"><img src="'+esc(group.qrSrc)+'" width="453" height="453" alt="'+esc(group.name)+'加群二维码" decoding="async"></div><span class="qr-frame-note">✦ 扫码，遇见同频的伙伴 ✦</span></div><figcaption>跳转不成功？保存二维码后，在 QQ 扫一扫中从相册识别。</figcaption><a class="qr-save" href="'+esc(group.qrSrc)+'" download="'+esc(group.name)+'-加群二维码.png">↓ 保存本群二维码</a></figure>':'';
  return '<article class="community-group"><div class="eyebrow">QQ GROUP '+String(index+1).padStart(2,'0')+'</div><h2>'+esc(group.name)+'</h2><p>'+esc(group.description||'加入社群，和伙伴一起开启社团生活。')+'</p>'+qr+'<div class="actions"><a class="primary" data-group-join href="'+esc(group.url)+'" target="_blank" rel="noopener noreferrer">加入'+esc(group.name)+' ↗</a><button class="secondary" data-copy-group="'+index+'">复制本群邀请</button></div></article>';
}).join('')+'</div>';}

return Object.freeze({groups:availableGroups,render:renderGroups});
})();
