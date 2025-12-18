/**
 * 提取UI元素 - 从手机获取UI层级并查找关键元素
 */

const { execSync } = require('child_process');
const fs = require('fs');

// 关键元素列表
const keyElements = [
  '通讯录',
  '添加客户',
  '新的客户',
  '查看',
  '通过验证',
  '完成',
  '智界Aigc客户群',
  '天天一泉～小石榴',
  '消息',
  '工作台',
  '我'
];

console.log('🔍 开始提取UI元素...\n');

// 获取当前UI层级
console.log('📱 获取手机UI层级...');
execSync('adb shell uiautomator dump /sdcard/ui.xml');
execSync('adb pull /sdcard/ui.xml ./ui_current.xml');

const uiXml = fs.readFileSync('ui_current.xml', 'utf-8');
console.log('✅ UI层级已获取\n');

// 查找每个元素
const results = {};

keyElements.forEach(text => {
  console.log(`🔍 查找元素: "${text}"`);
  
  // 尝试匹配 text 属性
  const textRegex = new RegExp(`text="${text}"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"`, 'g');
  const textMatch = uiXml.match(textRegex);
  
  // 尝试匹配 content-desc 属性
  const descRegex = new RegExp(`content-desc="[^"]*${text}[^"]*"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"`, 'g');
  const descMatch = uiXml.match(descRegex);
  
  if (textMatch) {
    console.log(`  ✅ 找到 (text): ${textMatch.length} 个`);
    results[text] = {
      found: true,
      method: 'text',
      count: textMatch.length,
      matches: textMatch
    };
  } else if (descMatch) {
    console.log(`  ✅ 找到 (content-desc): ${descMatch.length} 个`);
    results[text] = {
      found: true,
      method: 'content-desc',
      count: descMatch.length,
      matches: descMatch
    };
  } else {
    console.log(`  ⚠️  未找到`);
    results[text] = {
      found: false
    };
  }
  console.log('');
});

// 保存结果
fs.writeFileSync('ui_elements.json', JSON.stringify(results, null, 2));

console.log('\n✅ 提取完成!');
console.log('📄 结果已保存到: ui_elements.json');
console.log('\n💡 提示:');
console.log('- 如果某些元素未找到,可能是当前界面不对');
console.log('- 请确保企业微信在前台运行');

