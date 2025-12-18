/**
 * 测试批量处理所有好友申请并邀请到群聊
 */

const { WeworkAutomation } = require('./wework_automation.js');
const config = require('./config.json');

console.log('🧪 开始测试批量处理功能...\n');

try {
  // Step 1: 导航到"新的客户"列表页面
  console.log('📍 Step 1: 导航到"新的客户"列表页面...');
  const navigateSuccess = WeworkAutomation.navigateToNewCustomers();
  
  if (!navigateSuccess) {
    console.error('❌ 导航到"新的客户"列表页面失败');
    process.exit(1);
  }
  
  console.log('✅ 已到达"新的客户"列表页面\n');
  
  // Step 2: 批量通过所有好友申请
  console.log('📍 Step 2: 批量通过所有好友申请...');
  const approvedCustomers = WeworkAutomation.approveAllCustomers();
  
  if (approvedCustomers.length === 0) {
    console.log('\n⚠️  没有新的好友申请需要处理');
    process.exit(0);
  }
  
  console.log(`\n✅ 已通过 ${approvedCustomers.length} 个好友申请\n`);
  
  // Step 3: 批量邀请到群聊
  console.log('📍 Step 3: 批量邀请到群聊...');
  const results = WeworkAutomation.inviteAllToGroup(approvedCustomers, config.groupName);
  
  console.log(`\n\n🎉 批量处理完成!`);
  console.log(`📊 统计:`);
  console.log(`  - 通过验证: ${approvedCustomers.length} 个`);
  console.log(`  - 邀请成功: ${results.success.length} 个`);
  console.log(`  - 邀请失败: ${results.failed.length} 个`);
  
} catch (error) {
  console.error('❌ 测试失败:', error.message);
  console.error(error.stack);
  process.exit(1);
}

