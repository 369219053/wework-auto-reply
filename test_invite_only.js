const { WeworkAutomation } = require('./wework_automation');

console.log('🧪 测试邀请到群聊功能...\n');

// 测试邀请"二进制刀仔"到群聊
const customerName = '二进制刀仔';
const targetGroup = '智界Aigc客户群（18）';

console.log(`📍 开始邀请: ${customerName} → ${targetGroup}\n`);

const success = WeworkAutomation.inviteToGroup(customerName, targetGroup);

if (success) {
  console.log(`\n✅ 邀请成功! ${customerName} 已被邀请到 ${targetGroup}`);
} else {
  console.log(`\n❌ 邀请失败! 请检查日志`);
}

