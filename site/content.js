// ★ 最常改的内容集中在这里：只改引号里的中文，不要删除逗号。
// 修改后保存，刷新浏览器即可；服务器使用外置 site/，不需要重编译 Java。
window.WELCOME_CONTENT = Object.freeze({
  schoolName: '示例学院',
  clubName: '计算机社团',
  year: '2026',
  heading: ['计算机社团', '欢迎你'],
  tagline: '热爱不设限，未来由你定义',
  eyebrow: 'WELCOME · 入社邀请',
  welcomeTitle: '你的故事，从这里开始。',
  description: '无论你会不会编程，只要对计算机世界感兴趣，都欢迎你来认识我们。',
  detail: '选择兴趣、留下初见、生成纪念卡，再为你期待的社团活动投一票。',
  startLabel: '开启我的入社之旅',
  groupLabel: '加入计协活动 QQ 群',
  // 群名、邀请链接和二维码在这里修改。二维码放在 site/assets/，不要重绘码点。
  // 每个群独立加入；第一个群 url 留空时使用后端原群链接。
  qqGroups: [
    { name: '计协招新交流群', description: '招新咨询、认识同学，了解入社消息。', url: '', qrSrc: '' },
    { name: '计算机协会', description: '演示占位链接，请替换成你自己的群邀请。', url: 'https://qm.qq.com/example-community', qrSrc: '' }
  ],
  footer: '保持好奇，让每一次相遇都成为新的开始。'
});
