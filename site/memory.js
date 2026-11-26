'use strict';
// 本机仅保存随机报名凭证和步骤，不保存姓名、班级、QQ 或表单。
window.WELCOME_MEMORY = (()=>{
 const key='club-remembered-v1',duration=30*24*60*60*1000;
 function clear(token){try{if(token){const record=JSON.parse(localStorage.getItem(key)||'null');if(record&&record.token!==token)return;}localStorage.removeItem(key);}catch{}}
 function load(){try{const record=JSON.parse(localStorage.getItem(key)||'null');if(!record)return null;if(record.version!==1||!/[0-9a-f]{64}/.test(record.token)||record.token.length!==64||!Number.isFinite(record.expiresAt)||record.expiresAt<=Date.now()||record.expiresAt>Date.now()+duration+60000||!Number.isInteger(record.step)||record.step<0||record.step>5){clear();return null;}return record;}catch{clear();return null;}}
 function save(token,step){if(!/^[0-9a-f]{64}$/.test(token)||!Number.isInteger(step)||step<0||step>5)return false;try{localStorage.setItem(key,JSON.stringify({version:1,token,step,expiresAt:Date.now()+duration}));return true;}catch{return false;}}
 return Object.freeze({load,save,clear});
})();
