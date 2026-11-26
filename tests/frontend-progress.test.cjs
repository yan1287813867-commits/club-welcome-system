'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const site=path.resolve(__dirname,'../site');
const member={id:'ABCDEF123456',name:'流程测试',major:'测试班',interests:['code'],vote:'share'};
async function load(values={},expired=false,localValues={},storageBlocked=false){
 const memory=new Map(Object.entries(values)),localMemory=new Map(Object.entries(localValues)),requests=[],handlers={},elements={};
 function element(id){return elements[id]||(elements[id]={innerHTML:'',textContent:'',classList:{add(){},remove(){}},setAttribute(){},querySelector(){return null;},addEventListener(event,fn){handlers[id+':'+event]=fn;}});}
 const window={addEventListener(){},scrollTo(){}};
 const document={body:{dataset:{}},querySelector:element,addEventListener(){}};
 const context=vm.createContext({window,document,sessionStorage:{getItem:k=>memory.get(k)??null,setItem:(k,v)=>memory.set(k,v),removeItem:k=>memory.delete(k)},localStorage:{getItem:k=>{if(storageBlocked)throw new Error('blocked');return localMemory.get(k)??null;},setItem:(k,v)=>{if(storageBlocked)throw new Error('blocked');localMemory.set(k,v);},removeItem:k=>localMemory.delete(k)},crypto:require('node:crypto').webcrypto,URL,Set,console,setTimeout,clearTimeout,fetch:async url=>{requests.push(url);return ({ok:!(expired&&url==='/api/me'),status:expired&&url==='/api/me'?401:200,json:async()=>url==='/api/me'?(expired?{error:'expired'}:member):url==='/api/poll'?{results:{project:0,workshop:0,share:1,challenge:0}}:{retentionDays:180,privacyContact:'测试',qqGroupUrl:'https://qm.qq.com/test'}});}});
 for(const name of ['content.js','memory.js','community.js','progress.js','app.js'])vm.runInContext(fs.readFileSync(path.join(site,name),'utf8'),context,{filename:name});
 await new Promise(setImmediate);
 return {memory,localMemory,requests,handlers,elements,document,context};
}
test('刷新或从 QQ 重建页面，已报名用户停留在第06步',async()=>{
 const page=await load({'club-token':'a'.repeat(64),'club-step':'5'});
 assert.equal(page.document.body.dataset.stage,'5');
 assert.match(page.elements['#app'].innerHTML,/community-groups/);
 assert.equal((page.elements['#app'].innerHTML.match(/data-group-join/g)||[]).length,2);
 assert.match(page.elements['#app'].innerHTML,/计协招新交流群/);
 assert.match(page.elements['#app'].innerHTML,/计算机协会/);
 assert.match(page.elements['#app'].innerHTML,/example-community/);
 assert.equal((page.elements['#app'].innerHTML.match(/data-copy-group=/g)||[]).length,2);
 assert.equal((page.elements['#app'].innerHTML.match(/class="qr-frame"/g)||[]).length,0);
});
test('从纪念卡进入社群会记住步骤，重新加载仍在社群',async()=>{
 const page=await load({'club-token':'a'.repeat(64),'club-step':'3'});
 await page.handlers['#app:click']({target:{closest:selector=>selector==='button'?{hasAttribute:key=>key==='data-next',dataset:{next:'5'}}:null}});
 assert.equal(page.memory.get('club-step'),'5');
 const reopened=await load(Object.fromEntries(page.memory));
 assert.equal(reopened.document.body.dataset.stage,'5');
});
test('投票未结束时保留第05步',async()=>{
 const page=await load({'club-token':'a'.repeat(64),'club-step':'4'});
 assert.equal(page.document.body.dataset.stage,'4');
 assert.match(page.elements['#app'].innerHTML,/社团团建与趣味活动/);
 assert.doesNotMatch(page.elements['#app'].innerHTML,/轻松的分享会/);
});
test('过期报名凭证不能凭记忆跳过报名',async()=>{
 const page=await load({'club-token':'a'.repeat(64),'club-step':'5'},true);
 assert.equal(page.document.body.dataset.stage,'0');
 assert.equal(page.memory.has('club-token'),false);
});
test('网络失败待重试的表单回到报名页',async()=>{
 const pending={token:'a'.repeat(64),name:'测试',major:'测试班',interests:['code'],consent:true};
 const page=await load({'club-token':pending.token,'club-step':'5','club-pending':JSON.stringify(pending)},true);
 assert.equal(page.document.body.dataset.stage,'2');
});
test('旧版本无步骤记录时仍可查看纪念卡',async()=>{
 const page=await load({'club-token':'a'.repeat(64)});
 assert.equal(page.document.body.dataset.stage,'3');
});
test('仅接受受支持的步骤，已报名用户不重复填写表单',async()=>{
 for(const value of ['1','2','999','null']){
  const page=await load({'club-token':'a'.repeat(64),'club-step':value});
  assert.equal(page.document.body.dataset.stage,'3');
 }
});
test('未报名的新访客不能伪造步骤直接进入群页',async()=>{
 const page=await load({'club-step':'5'});
 assert.equal(page.document.body.dataset.stage,'0');
});

module.exports={load};

const rememberedKey='club-remembered-v1';
test('旧标签页凭证自动迁移，完全关闭窗口后新会话可恢复06',async()=>{
 const first=await load({'club-token':'a'.repeat(64),'club-step':'5'});
 const fresh=await load({},false,Object.fromEntries(first.localMemory));
 assert.equal(fresh.document.body.dataset.stage,'5');
 assert.equal(fresh.memory.get('club-token'),'a'.repeat(64));
});
test('本机长期记忆不包含姓名、班级、QQ或提交表单',async()=>{
 const page=await load({'club-token':'a'.repeat(64),'club-step':'5'});
 const record=JSON.parse(page.localMemory.get(rememberedKey));
 assert.deepEqual(Object.keys(record).sort(),['expiresAt','step','token','version']);
 assert.equal(record.token.length,64);
});
test('记忆到期或格式破坏后不恢复凭证，首页仍有直接加群入口',async()=>{
 for(const record of [{version:1,token:'a'.repeat(64),step:5,expiresAt:Date.now()-1000},{version:1,token:'bad',step:5,expiresAt:Date.now()+10000}]){
  const page=await load({},false,{[rememberedKey]:JSON.stringify(record)});
  assert.equal(page.document.body.dataset.stage,'0');
  assert.match(page.elements['#app'].innerHTML,/href="\/groups.html"/);
 }
});
test('后台已删除的报名不会从长期凭证继续恢复',async()=>{
 const record={version:1,token:'a'.repeat(64),step:5,expiresAt:Date.now()+10000};
 const page=await load({},true,{[rememberedKey]:JSON.stringify(record)});
 assert.equal(page.document.body.dataset.stage,'0');assert.equal(page.localMemory.has(rememberedKey),false);
});
test('禁用持久存储时保留标签页流程，并可直接进入公开群页',async()=>{
 const page=await load({'club-token':'a'.repeat(64),'club-step':'5'},false,{},true);
 assert.equal(page.document.body.dataset.stage,'5');
 const fresh=await load({},false,{},true);
 assert.match(fresh.elements['#app'].innerHTML,/直接查看加群二维码/);
});
test('独立加群页不依赖任何报名凭证，也不请求或创建报名',async()=>{
 const page=await load();page.requests.length=0;
 vm.runInContext(fs.readFileSync(path.join(site,'groups.js'),'utf8'),page.context,{filename:'groups.js'});
 await new Promise(setImmediate);
 assert.deepEqual(page.requests,['/api/config']);
 assert.equal((page.elements['#app'].innerHTML.match(/data-group-join/g)||[]).length,2);
 assert.match(page.elements['#app'].innerHTML,/无需重复填写/);
});

test('清除本机记忆后不保留报名凭证，也不删除服务器记录',async()=>{
 const page=await load({'club-token':'a'.repeat(64),'club-step':'5'});page.requests.length=0;
 await page.handlers['#app:click']({target:{closest:selector=>selector==='button'?{hasAttribute:key=>key==='data-forget',dataset:{}}:null}});
 assert.equal(page.localMemory.has(rememberedKey),false);assert.equal(page.memory.has('club-token'),false);
 assert.equal(page.document.body.dataset.stage,'0');assert.equal(page.requests.length,0);
});
