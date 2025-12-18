/**
 * 测试邀请到群聊功能
 */

const { WeworkAutomation } = require('./wework_automation.js');

console.log('🧪 开始测试邀请到群聊功能...\n');

try {
  // 导航到"新的客户"列表页面
  console.log('📍 Step 1: 导航到"新的客户"列表页面...');
  const navigateSuccess = WeworkAutomation.navigateToNewCustomers();
  
  if (!navigateSuccess) {
    console.error('❌ 导航到"新的客户"列表页面失败');
    process.exit(1);
  }
  
  console.log('✅ 已到达"新的客户"列表页面\n');
  
  // 处理第一个新客户
  console.log('📍 Step 2: 处理第一个新客户...');
  const customerName = WeworkAutomation.processNewCustomer(0);
  
  if (customerName) {
    console.log(`\n🎉 测试成功! 已处理客户: ${customerName}`);
  } else {
    console.log('\n⚠️  没有新客户需要处理');
  }
  
} catch (error) {
  console.error('❌ 测试失败:', error.message);
  console.error(error.stack);
  process.exit(1);
}

