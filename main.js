/**
 * 企业微信自动化主流程
 * 功能: 监控新客户 → 通过申请 → 邀请进群
 */

const {
  loadConfig,
  saveCustomer,
  updateCustomerInvited,
  clickText,
  inputText,
  pressBack,
  sleep,
  screenshot,
  findElementByText
} = require('./wework_automation');

// 全局配置
let config = null;

// 初始化
function init() {
  config = loadConfig();
  console.log('🚀 企业微信自动化启动!');
  console.log(`📋 目标群聊: ${config.targetGroup}`);
  console.log(`⏱️  检查间隔: ${config.checkInterval}秒`);
  console.log('-----------------------------------');
}

// 步骤1: 检查新客户
function checkNewCustomers() {
  console.log('\n🔍 检查新客户...');
  
  // 点击通讯录
  if (!clickText('通讯录', 3000)) {
    console.log('⚠️  未找到通讯录,可能已在通讯录页面');
  }
  sleep(1000);
  
  // 点击添加客户
  if (!clickText('添加客户')) {
    console.error('❌ 未找到"添加客户"按钮');
    return false;
  }
  sleep(1000);
  
  // 点击"新的客户"标签
  if (!clickText('新的客户')) {
    console.error('❌ 未找到"新的客户"标签');
    pressBack();
    return false;
  }
  sleep(1000);
  
  // 查找"查看"按钮
  const viewButton = findElementByText('查看');
  if (!viewButton) {
    console.log('✅ 暂无新客户');
    pressBack();
    pressBack();
    return false;
  }
  
  console.log('🎉 发现新客户!');
  return true;
}

// 步骤2: 通过好友申请
function approveCustomer() {
  console.log('\n✅ 通过好友申请...');
  
  // 点击"查看"
  if (!clickText('查看')) {
    console.error('❌ 点击查看失败');
    return null;
  }
  sleep(1500);
  
  // 截图保存客户信息页面
  if (config.debug) {
    screenshot('customer_info.png');
  }
  
  // TODO: 这里需要提取客户名称
  // 暂时使用时间戳作为标识
  const customerName = `客户_${Date.now()}`;
  
  // 点击"同意"或"通过"
  if (!clickText('同意') && !clickText('通过')) {
    console.error('❌ 未找到同意按钮');
    pressBack();
    return null;
  }
  sleep(1000);
  
  // 记录客户信息
  saveCustomer(customerName);
  
  console.log(`✅ 已通过好友申请: ${customerName}`);
  return customerName;
}

// 步骤3: 返回消息页面
function goToMessagePage() {
  console.log('\n📱 返回消息页面...');
  
  // 多次返回,确保回到主页
  pressBack();
  sleep(500);
  pressBack();
  sleep(500);
  pressBack();
  sleep(500);
  
  // 点击底部"消息"标签
  if (!clickText('消息')) {
    console.log('⚠️  未找到消息标签,可能已在消息页面');
  }
  sleep(1000);
  
  console.log('✅ 已返回消息页面');
}

// 步骤4: 找到目标群聊
function findTargetGroup() {
  console.log(`\n🔍 查找群聊: ${config.targetGroup}...`);
  
  // 点击搜索框(如果有)
  // 或者直接在列表中查找
  const groupElement = findElementByText(config.targetGroup);
  if (!groupElement) {
    console.error(`❌ 未找到群聊: ${config.targetGroup}`);
    return false;
  }
  
  // 点击进入群聊
  clickText(config.targetGroup);
  sleep(1500);
  
  console.log(`✅ 已进入群聊: ${config.targetGroup}`);
  return true;
}

// 步骤5: 邀请客户进群
function inviteToGroup(customerName) {
  console.log(`\n👥 邀请客户进群: ${customerName}...`);
  
  // 点击"+"按钮
  if (!clickText('+')) {
    console.error('❌ 未找到"+"按钮');
    return false;
  }
  sleep(1000);
  
  // 在搜索框输入客户名称
  // TODO: 需要找到搜索框的坐标
  inputText(customerName);
  sleep(1000);
  
  // 点击搜索结果中的客户
  if (!clickText(customerName)) {
    console.error(`❌ 未找到客户: ${customerName}`);
    pressBack();
    return false;
  }
  sleep(500);
  
  // 点击"确定"
  if (!clickText('确定')) {
    console.error('❌ 未找到确定按钮');
    pressBack();
    return false;
  }
  sleep(1000);
  
  // 判断是否需要点击"邀请"按钮
  const inviteButton = findElementByText('邀请');
  if (inviteButton) {
    console.log('🔍 检测到"邀请"按钮,点击确认...');
    clickText('邀请');
    sleep(1000);
  }
  
  // 更新客户邀请状态
  updateCustomerInvited(customerName);
  
  console.log(`✅ 邀请成功: ${customerName}`);
  return true;
}

// 主循环
function mainLoop() {
  init();
  
  while (true) {
    try {
      // 检查新客户
      const hasNewCustomer = checkNewCustomers();
      
      if (hasNewCustomer) {
        // 通过好友申请
        const customerName = approveCustomer();
        
        if (customerName) {
          // 返回消息页面
          goToMessagePage();
          
          // 找到目标群聊
          if (findTargetGroup()) {
            // 邀请进群
            inviteToGroup(customerName);
            
            // 返回消息页面
            pressBack();
            sleep(1000);
          }
        }
      }
      
      // 等待下一次检查
      console.log(`\n⏳ 等待 ${config.checkInterval} 秒后继续检查...`);
      sleep(config.checkInterval * 1000);
      
    } catch (error) {
      console.error('❌ 发生错误:', error.message);
      if (config.debug) {
        screenshot('error.png');
      }
      sleep(5000);
    }
  }
}

// 启动
mainLoop();

