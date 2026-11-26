// 只记录当前步骤，不写入姓名、QQ 或报名凭证。
'use strict';
window.WELCOME_PROGRESS = Object.freeze({
  restore(value, member, pending) {
    if (!member) return pending ? 2 : 0;
    const step = /^[0-5]$/.test(String(value)) ? Number(value) : 3;
    return [0,3,4,5].includes(step) ? step : 3;
  }
});
