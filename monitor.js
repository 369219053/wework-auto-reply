#!/usr/bin/env node

/**
 * 企业微信自动化监控服务
 * 功能: 持续监控新客户申请,自动通过验证并邀请入群
 */

const { ADBHelper, DataManager, WeworkAutomation } = require('./wework_automation');

// 监控状态
let isRunning = false;
let checkCount = 0;

/**
 * 监控服务主函数
 */
async function monitorService() {
  console.log('\n' + '='.repeat(60));
  console.log('🚀 企业微信自动化监控服务');
  console.log('='.repeat(60));
  
  try {
    // 加载配置
    const config = DataManager.loadConfig();
    console.log(`\n📋 配置信息:`);
    console.log(`   目标群聊: ${config.targetGroup}`);
    console.log(`   检查间隔: ${config.checkInterval}秒`);
    console.log(`   调试模式: ${config.debug ? '开启' : '关闭'}`);
    
    // 检查ADB连接
    console.log(`\n🔌 检查ADB连接...`);
    const devices = ADBHelper.exec('devices');
    if (!devices.includes('device')) {
      console.error('❌ ADB未连接设备,请检查手机连接!');
      process.exit(1);
    }
    console.log('✅ ADB连接正常');

    // 启动企业微信
    console.log(`\n📱 准备企业微信...`);
    ADBHelper.launchWework();

    // 启动监控
    isRunning = true;
    console.log(`\n✅ 监控服务已启动,每${config.checkInterval}秒检查一次新客户...\n`);
    
    // 主循环
    while (isRunning) {
      try {
        await checkNewCustomers(config);
      } catch (error) {
        console.error('\n❌ 检查过程出错:', error.message);
        if (config.debug) {
          console.error(error.stack);
        }
      }
      
      // 等待下次检查
      console.log(`\n⏰ 等待${config.checkInterval}秒后进行下次检查...`);
      await sleep(config.checkInterval * 1000);
    }
    
  } catch (error) {
    console.error('\n❌ 监控服务启动失败:', error.message);
    process.exit(1);
  }
}

/**
 * 检查新客户
 */
async function checkNewCustomers(config) {
  checkCount++;
  console.log('\n' + '-'.repeat(60));
  console.log(`🔍 第 ${checkCount} 次检查 - ${new Date().toLocaleString()}`);
  console.log('-'.repeat(60));

  try {
    // 确保企业微信正在运行
    if (!ADBHelper.isWeworkRunning()) {
      console.log('⚠️  企业微信未运行,正在启动...');
      ADBHelper.launchWework();
    }

    // 导航到"新的客户"列表
    WeworkAutomation.navigateToNewCustomers();
    
    // 获取新客户列表
    const viewButtons = WeworkAutomation.getNewCustomersList();
    
    if (viewButtons.length === 0) {
      console.log('✅ 当前没有新客户申请');
      // 返回消息页面
      WeworkAutomation.returnToMessages();
      return;
    }
    
    console.log(`\n📢 发现 ${viewButtons.length} 个新客户申请,开始处理...\n`);
    
    // 处理每个新客户
    let processedCount = 0;
    for (let i = 0; i < viewButtons.length; i++) {
      const customerName = WeworkAutomation.processNewCustomer(i);
      if (customerName) {
        processedCount++;

        // 如果成功处理且不是最后一个,返回到"新的客户"列表
        if (i < viewButtons.length - 1) {
          WeworkAutomation.navigateToNewCustomers();
        }
      }
      // 如果客户已处理过(返回null),已经在"新的客户"列表页了,不需要再导航
    }
    
    console.log(`\n✅ 本次检查完成,成功处理 ${processedCount}/${viewButtons.length} 个客户`);
    
  } catch (error) {
    console.error('❌ 检查新客户时出错:', error.message);
    if (config.debug) {
      console.error(error.stack);
    }
    
    // 尝试返回消息页面
    try {
      WeworkAutomation.returnToMessages();
    } catch (e) {
      console.error('❌ 返回消息页面失败');
    }
  }
}

/**
 * 延迟函数
 */
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * 优雅退出
 */
function gracefulShutdown() {
  console.log('\n\n🛑 收到退出信号,正在停止监控服务...');
  isRunning = false;
  console.log('✅ 监控服务已停止');
  process.exit(0);
}

// 监听退出信号
process.on('SIGINT', gracefulShutdown);
process.on('SIGTERM', gracefulShutdown);

// 启动监控服务
monitorService().catch(error => {
  console.error('\n❌ 监控服务异常退出:', error.message);
  process.exit(1);
});

