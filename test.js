/**
 * 测试脚本 - 用于调试和验证各个功能
 */

const {
  loadConfig,
  adb,
  screenshot,
  dumpUI,
  findElementByText,
  clickText,
  sleep
} = require('./wework_automation');

console.log('🧪 开始测试...\n');

// 测试1: 检查ADB连接
console.log('1️⃣  测试ADB连接...');
const devices = adb('devices');
console.log(devices);
if (devices.includes('device')) {
  console.log('✅ ADB连接正常\n');
} else {
  console.error('❌ ADB未连接\n');
  process.exit(1);
}

// 测试2: 加载配置
console.log('2️⃣  测试加载配置...');
const config = loadConfig();
console.log('配置内容:', config);
console.log('✅ 配置加载成功\n');

// 测试3: 截图
console.log('3️⃣  测试截图功能...');
screenshot('test_screenshot.png');
console.log('✅ 截图成功\n');

// 测试4: 获取UI层级
console.log('4️⃣  测试UI层级获取...');
const ui = dumpUI();
if (ui) {
  console.log('UI层级长度:', ui.length);
  console.log('✅ UI层级获取成功\n');
} else {
  console.error('❌ UI层级获取失败\n');
}

// 测试5: 查找元素
console.log('5️⃣  测试查找元素...');
console.log('请确保企业微信已打开...');
sleep(2000);

const elements = ['消息', '通讯录', '工作台', '我'];
elements.forEach(text => {
  const element = findElementByText(text);
  if (element) {
    console.log(`✅ 找到元素: ${text} at (${element.x}, ${element.y})`);
  } else {
    console.log(`⚠️  未找到元素: ${text}`);
  }
});

console.log('\n🎉 测试完成!');
console.log('\n💡 提示:');
console.log('- 如果所有测试都通过,说明基础功能正常');
console.log('- 如果找不到元素,请确保企业微信已打开');
console.log('- 可以查看 test_screenshot.png 了解当前界面');

