/**
 * 测试批量邀请功能
 */

const { WeworkAutomation } = require('./wework_automation.js');
const config = require('./config.json');

console.log('🧪 测试批量邀请功能...\n');

// 刚才通过验证的客户
const approvedCustomers = ['创视空间', '二进制刀仔'];

console.log(`📋 准备邀请 ${approvedCustomers.length} 个客户到群聊: ${config.groupName}\n`);
approvedCustomers.forEach((name, i) => console.log(`  ${i + 1}. ${name}`));

console.log('\n开始批量邀请...\n');

try {
  const results = WeworkAutomation.inviteAllToGroup(approvedCustomers, config.groupName);
  
  console.log(`\n\n🎉 批量邀请完成!`);
  console.log(`📊 统计:`);
  console.log(`  - 邀请成功: ${results.success.length} 个`);
  console.log(`  - 邀请失败: ${results.failed.length} 个`);
  
} catch (error) {
  console.error('❌ 测试失败:', error.message);
  console.error(error.stack);
  process.exit(1);
}

