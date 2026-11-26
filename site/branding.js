'use strict';
// 页眉统一从 content.js 取学校和社团名称；校徽使用随项目保存的官网原图。
const brandContent = window.WELCOME_CONTENT;
document.querySelectorAll('[data-school-name]').forEach(el => el.textContent = brandContent.schoolName);
document.querySelectorAll('[data-club-name]').forEach(el => el.textContent = '（' + brandContent.clubName + '）');
