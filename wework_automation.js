/**
 * 企业微信自动化核心工具模块
 * 功能: ADB命令封装、UI元素查找、自动化流程执行
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// 加载UI坐标配置
const coordsPath = path.join(__dirname, 'ui_elements_coords.json');
const coords = JSON.parse(fs.readFileSync(coordsPath, 'utf8'));

// 配置文件路径
const CONFIG_PATH = path.join(__dirname, 'config.json');
const CUSTOMERS_PATH = path.join(__dirname, 'customers.json');

/**
 * ADB命令执行封装
 */
class ADBHelper {
  /**
   * 执行ADB命令
   */
  static exec(command, options = {}) {
    try {
      const result = execSync(`adb ${command}`, {
        encoding: 'utf8',
        timeout: options.timeout || 10000,
        ...options
      });
      return result.trim();
    } catch (error) {
      console.error(`❌ ADB命令执行失败: ${command}`);
      console.error(error.message);
      throw error;
    }
  }

  /**
   * 点击屏幕坐标
   */
  static tap(x, y, delay = 1500) {
    console.log(`👆 点击坐标: [${x}, ${y}]`);
    this.exec(`shell input tap ${x} ${y}`);
    this.sleep(delay);
  }

  /**
   * 按返回键
   */
  static back(delay = 1000) {
    console.log('⬅️  按返回键');
    this.exec('shell input keyevent 4');
    this.sleep(delay);
  }

  /**
   * 输入文本
   */
  static inputText(text, delay = 1000) {
    console.log(`⌨️  输入文本: ${text}`);
    const escapedText = text.replace(/\s/g, '%s');
    this.exec(`shell input text "${escapedText}"`);
    this.sleep(delay);
  }

  /**
   * 获取UI层级结构
   */
  static dumpUI() {
    this.exec('shell uiautomator dump /sdcard/ui.xml');
    const localPath = path.join(__dirname, 'ui_current.xml');
    this.exec(`pull /sdcard/ui.xml ${localPath}`);
    return fs.readFileSync(localPath, 'utf8');
  }

  /**
   * 延迟
   */
  static sleep(ms) {
    execSync(`sleep ${ms / 1000}`, { encoding: 'utf8' });
  }

  /**
   * 启动企业微信
   */
  static launchWework() {
    console.log('📱 启动企业微信...');
    this.exec('shell am start -n com.tencent.wework/.launch.LaunchSplashActivity');
    this.sleep(3000); // 等待应用启动
    console.log('✅ 企业微信已启动');
  }

  /**
   * 检查当前应用是否是企业微信
   */
  static isWeworkRunning() {
    try {
      const result = this.exec('shell dumpsys window | grep mCurrentFocus');
      return result.includes('com.tencent.wework');
    } catch (error) {
      return false;
    }
  }
}

/**
 * UI元素查找和操作
 */
class UIHelper {
  /**
   * 在UI XML中查找包含指定文本的元素
   * @param {string} uiXml - UI XML内容
   * @param {string} text - 要查找的文本
   * @param {object} options - 可选参数
   * @param {string[]} options.excludeAfterText - 排除在某些文本之后的元素(用于排除"最近联系人"等区域)
   */
  static findElementByText(uiXml, text, options = {}) {
    const regex = new RegExp(`text="${text}"[^>]*bounds="\\[([0-9]+),([0-9]+)\\]\\[([0-9]+),([0-9]+)\\]"`, 'g');
    const matches = [];
    let match;

    // 收集所有匹配的元素
    while ((match = regex.exec(uiXml)) !== null) {
      const x1 = parseInt(match[1]);
      const y1 = parseInt(match[2]);
      const x2 = parseInt(match[3]);
      const y2 = parseInt(match[4]);
      const centerX = Math.floor((x1 + x2) / 2);
      const centerY = Math.floor((y1 + y2) / 2);

      matches.push({
        x: centerX,
        y: centerY,
        y1: y1,
        bounds: `[${x1},${y1}][${x2},${y2}]`,
        index: match.index
      });
    }

    if (matches.length === 0) {
      return null;
    }

    // 如果指定了排除规则,使用Y坐标过滤
    if (options.excludeAfterText && options.excludeAfterText.length > 0) {
      // 找到所有排除区域的Y坐标范围
      const excludeRanges = [];
      for (const excludeText of options.excludeAfterText) {
        const excludeRegex = new RegExp(`text="${excludeText}"[^>]*bounds="\\[([0-9]+),([0-9]+)\\]\\[([0-9]+),([0-9]+)\\]"`);
        const excludeMatch = uiXml.match(excludeRegex);
        if (excludeMatch) {
          const excludeY1 = parseInt(excludeMatch[2]);
          const excludeY2 = parseInt(excludeMatch[4]);
          excludeRanges.push({
            text: excludeText,
            y1: excludeY1,
            y2: excludeY2
          });
          console.log(`🔍 排除区域"${excludeText}": Y=${excludeY1}-${excludeY2}`);
        }
      }

      // 过滤掉在排除区域Y坐标范围内的元素
      const filteredMatches = matches.filter(m => {
        for (const range of excludeRanges) {
          // 如果元素的Y坐标在排除区域之后的200像素内,则排除
          if (m.y1 > range.y1 && m.y1 < range.y2 + 200) {
            console.log(`🚫 排除元素: ${m.bounds} (在"${range.text}"区域内,Y=${m.y1})`);
            return false;
          }
        }
        return true;
      });

      if (filteredMatches.length > 0) {
        console.log(`✅ 过滤后找到 ${filteredMatches.length} 个元素,选择第一个: ${filteredMatches[0].bounds}`);
        return filteredMatches[0];
      } else {
        console.log(`❌ 过滤后没有找到符合条件的元素`);
        return null;
      }
    }

    // 如果有多个匹配,打印所有坐标
    if (matches.length > 1) {
      console.log(`🔍 找到 ${matches.length} 个"${text}"元素:`);
      matches.forEach((m, i) => console.log(`  ${i + 1}. ${m.bounds}`));
    }

    // 返回第一个匹配的元素
    return matches[0];
  }

  /**
   * 查找所有包含指定文本的元素
   */
  static findAllElementsByText(uiXml, text) {
    const regex = new RegExp(`text="${text}"[^>]*bounds="\\[([0-9]+),([0-9]+)\\]\\[([0-9]+),([0-9]+)\\]"`, 'g');
    const elements = [];
    let match;

    while ((match = regex.exec(uiXml)) !== null) {
      const x1 = parseInt(match[1]);
      const y1 = parseInt(match[2]);
      const x2 = parseInt(match[3]);
      const y2 = parseInt(match[4]);
      const centerX = Math.floor((x1 + x2) / 2);
      const centerY = Math.floor((y1 + y2) / 2);

      elements.push({ x: centerX, y: centerY, bounds: `[${x1},${y1}][${x2},${y2}]` });
    }

    return elements;
  }

  /**
   * 提取所有文本内容
   */
  static extractAllTexts(uiXml) {
    const regex = /text="([^"]+)"/g;
    const texts = new Set();
    let match;

    while ((match = regex.exec(uiXml)) !== null) {
      if (match[1] && match[1].trim()) {
        texts.add(match[1]);
      }
    }

    return Array.from(texts);
  }

  /**
   * 检查元素是否存在
   */
  static elementExists(uiXml, text) {
    return uiXml.includes(`text="${text}"`);
  }

  /**
   * 在"今天"分组下查找客户名称
   * 避免在"最近联系人"等其他区域误匹配
   */
  static findCustomerInTodayGroup(uiXml, customerName) {
    // 1. 找到"今天"分组的位置
    const todayMatch = uiXml.match(/text="今天"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/);
    if (!todayMatch) {
      console.error('❌ 未找到"今天"分组');
      return null;
    }

    const todayY1 = parseInt(todayMatch[2]); // "今天"文本的起始Y坐标
    const todayY2 = parseInt(todayMatch[4]); // "今天"文本的结束Y坐标
    console.log(`🔍 "今天"分组标题: Y=${todayY1}-${todayY2}`);

    // 2. 找到下一个分组的位置(可能是"12-15"、"昨天"等日期分组,或者"最近联系人")
    // 匹配所有可能的分组标题
    const groupRegex = /text="(今天|昨天|12-15|12-14|12-13|最近联系人|[A-Z])"[^>]*resource-id="com\.tencent\.wework:id\/(glz|n_s)"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/g;
    const groups = [];
    let groupMatch;

    while ((groupMatch = groupRegex.exec(uiXml)) !== null) {
      const groupName = groupMatch[1];
      const groupY1 = parseInt(groupMatch[4]); // 修正: 第4个捕获组是Y1
      const groupY2 = parseInt(groupMatch[6]); // 修正: 第6个捕获组是Y2
      groups.push({
        name: groupName,
        y1: groupY1,
        y2: groupY2
      });
    }

    // 按Y坐标排序
    groups.sort((a, b) => a.y1 - b.y1);

    console.log(`🔍 找到 ${groups.length} 个分组:`);
    groups.forEach(g => console.log(`  - ${g.name}: Y=${g.y1}-${g.y2}`));

    // 找到"今天"分组的索引
    const todayIndex = groups.findIndex(g => g.name === '今天');
    if (todayIndex === -1) {
      console.error('❌ 未在分组列表中找到"今天"');
      return null;
    }

    // 确定"今天"分组的范围
    const todayGroupEnd = todayY2; // "今天"标题的结束位置
    const nextGroupStart = todayIndex + 1 < groups.length ? groups[todayIndex + 1].y1 : 9999; // 下一个分组的开始位置
    console.log(`🔍 "今天"分组范围: Y=${todayGroupEnd}-${nextGroupStart}`);

    // 3. 查找所有匹配客户名称的元素
    const regex = new RegExp(`text="${customerName}"[^>]*bounds="\\[([0-9]+),([0-9]+)\\]\\[([0-9]+),([0-9]+)\\]"`, 'g');
    const matches = [];
    let match;

    while ((match = regex.exec(uiXml)) !== null) {
      const x1 = parseInt(match[1]);
      const y1 = parseInt(match[2]);
      const x2 = parseInt(match[3]);
      const y2 = parseInt(match[4]);
      const centerX = Math.floor((x1 + x2) / 2);
      const centerY = Math.floor((y1 + y2) / 2);

      matches.push({
        x: centerX,
        y: centerY,
        y1: y1,
        y2: y2,
        bounds: `[${x1},${y1}][${x2},${y2}]`
      });
    }

    console.log(`🔍 找到 ${matches.length} 个"${customerName}"元素`);

    // 4. 找到Y坐标在"今天"分组范围内的元素
    for (const element of matches) {
      console.log(`  - 坐标: ${element.bounds}, Y: ${element.y1}-${element.y2}`);
      // 使用 >= 和 < 来判断范围,包含边界
      if (element.y1 >= todayGroupEnd && element.y1 < nextGroupStart) {
        console.log(`✅ 选择"今天"分组下的元素: ${element.bounds}`);
        return { x: element.x, y: element.y, bounds: element.bounds };
      }
    }

    console.error(`❌ 未在"今天"分组范围内找到"${customerName}"`);
    return null;
  }
}

/**
 * 数据管理
 */
class DataManager {
  /**
   * 加载配置
   */
  static loadConfig() {
    try {
      return JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
    } catch (error) {
      console.error('❌ 加载配置文件失败:', error.message);
      throw error;
    }
  }

  /**
   * 加载客户记录
   */
  static loadCustomers() {
    try {
      const data = JSON.parse(fs.readFileSync(CUSTOMERS_PATH, 'utf8'));
      return data.customers || [];
    } catch (error) {
      return [];
    }
  }

  /**
   * 保存客户记录
   */
  static saveCustomer(customerData) {
    const data = { customers: this.loadCustomers() };
    data.customers.push({
      name: customerData.name,
      company: customerData.company || '',
      approvedAt: new Date().toISOString(),
      invitedAt: null,
      status: 'approved'
    });
    fs.writeFileSync(CUSTOMERS_PATH, JSON.stringify(data, null, 2));
    console.log(`📝 已记录客户: ${customerData.name}`);
  }

  /**
   * 更新客户邀请状态
   */
  static updateCustomerInvited(customerName) {
    const data = { customers: this.loadCustomers() };
    const customer = data.customers.find(c => c.name === customerName && c.status === 'approved');
    if (customer) {
      customer.invitedAt = new Date().toISOString();
      customer.status = 'completed';
      fs.writeFileSync(CUSTOMERS_PATH, JSON.stringify(data, null, 2));
      console.log(`✅ 已更新客户邀请状态: ${customerName}`);
    }
  }

  /**
   * 检查客户是否已处理
   */
  static isCustomerProcessed(customerName) {
    const customers = this.loadCustomers();
    return customers.some(c => c.name === customerName);
  }
}

/**
 * 企业微信自动化流程
 */
class WeworkAutomation {
  /**
   * 获取当前Activity名称
   */
  static getCurrentActivity() {
    try {
      const output = ADBHelper.exec('shell dumpsys window | grep mCurrentFocus', { encoding: 'utf8' });
      // 输出格式: mCurrentFocus=Window{xxx u0 com.tencent.wework/com.tencent.wework.xxx.XxxActivity type=1 }
      const match = output.match(/com\.tencent\.wework\/([^\s]+)/);
      if (match && match[1]) {
        return match[1];
      }
    } catch (error) {
      console.error('❌ 获取Activity失败:', error.message);
    }
    return null;
  }

  /**
   * 检查当前页面类型 (基于Activity + 文本特征)
   */
  static detectCurrentPage() {
    // 1. 优先使用Activity识别
    const activity = this.getCurrentActivity();

    if (activity) {
      // 客户详情页 - 唯一Activity
      if (activity.includes('ContactDetailBaseContentActivity')) {
        return 'customer_detail';
      }

      // 通过验证后的页面 - 唯一Activity
      if (activity.includes('ContactRemarkAndOtherInfoEditActivity')) {
        return 'after_approve';
      }

      // 添加客户/新的客户页面 - 需要结合文本判断
      if (activity.includes('FriendAddTabActivity')) {
        const uiXml = ADBHelper.dumpUI();
        const allTexts = UIHelper.extractAllTexts(uiXml);

        // 检查是否在"新的客户"列表
        if (allTexts.includes('新的客户') && allTexts.includes('查看')) {
          return 'new_customers_list';
        }

        // 在添加客户页面
        if (allTexts.includes('添加客户') && allTexts.includes('新的客户')) {
          return 'add_customer_page';
        }
      }

      // 主Activity - 需要结合文本判断
      if (activity.includes('WwMainActivity')) {
        const uiXml = ADBHelper.dumpUI();
        const allTexts = UIHelper.extractAllTexts(uiXml);

        // 通讯录页面
        if (allTexts.includes('添加客户') || allTexts.includes('我的客户')) {
          return 'contacts_page';
        }

        // 消息页面
        if (allTexts.includes('消息') && allTexts.includes('通讯录') && allTexts.includes('工作台')) {
          return 'message_page';
        }
      }
    }

    // 2. 兜底使用文本识别
    const uiXml = ADBHelper.dumpUI();
    const allTexts = UIHelper.extractAllTexts(uiXml);

    if (allTexts.includes('新的客户') && allTexts.includes('查看')) {
      return 'new_customers_list';
    }
    if (allTexts.includes('通过验证') && (allTexts.includes('发消息') || allTexts.includes('语音通话'))) {
      return 'customer_detail';
    }
    if (allTexts.includes('完成') && allTexts.includes('备注')) {
      return 'after_approve';
    }

    return 'unknown';
  }

  /**
   * 智能导航到"新的客户"列表
   */
  static navigateToNewCustomers() {
    console.log('\n📱 智能导航到"新的客户"列表...');

    const maxRetry = 3;
    for (let retry = 0; retry < maxRetry; retry++) {
      // 检查当前页面
      const currentPage = this.detectCurrentPage();
      console.log(`🔍 当前页面: ${currentPage}`);

      if (currentPage === 'new_customers_list') {
        console.log('✅ 已在"新的客户"列表页面');
        return true;
      }

      // 根据当前页面执行相应操作
      if (currentPage === 'message_page' || currentPage === 'contacts_page') {
        // Step 1: 确保在通讯录页面
        if (currentPage === 'message_page') {
          console.log('👆 点击通讯录标签...');
          const contactsTab = coords.step1_message_page.elements['通讯录'];
          ADBHelper.tap(contactsTab.center[0], contactsTab.center[1], 1500);
        }

        // Step 2: 点击添加客户
        const uiXml = ADBHelper.dumpUI();
        if (UIHelper.elementExists(uiXml, '添加客户')) {
          console.log('👆 点击添加客户...');
          const addCustomer = coords.step2_contacts_page.elements['添加客户'];
          ADBHelper.tap(addCustomer.center[0], addCustomer.center[1], 1500);
        } else {
          console.log('⚠️  未找到"添加客户"按钮,重试...');
          continue;
        }

        // Step 3: 点击新的客户
        const uiXml2 = ADBHelper.dumpUI();
        if (UIHelper.elementExists(uiXml2, '新的客户')) {
          console.log('👆 点击新的客户标签...');
          const newCustomersTab = coords.step3_add_customer_page.elements['新的客户_tab'];
          ADBHelper.tap(newCustomersTab.center[0], newCustomersTab.center[1], 1500);
        } else {
          console.log('⚠️  未找到"新的客户"标签,重试...');
          continue;
        }

        // 验证是否成功到达
        ADBHelper.sleep(1000);
        const finalPage = this.detectCurrentPage();
        if (finalPage === 'new_customers_list') {
          console.log('✅ 成功到达"新的客户"列表页面');
          return true;
        }

      } else if (currentPage === 'add_customer_page') {
        // 已经在添加客户页面,直接点击新的客户
        console.log('👆 点击新的客户标签...');
        const newCustomersTab = coords.step3_add_customer_page.elements['新的客户_tab'];
        ADBHelper.tap(newCustomersTab.center[0], newCustomersTab.center[1], 1500);

      } else if (currentPage === 'customer_detail') {
        // 在客户详情页,按返回键
        console.log('⬅️  从客户详情页返回...');
        ADBHelper.back();

      } else {
        // 未知页面,尝试返回到消息页面
        console.log('⚠️  未知页面,尝试返回主页...');
        ADBHelper.launchWework();
        ADBHelper.sleep(2000);
      }
    }

    console.error('❌ 导航到"新的客户"列表失败');
    return false;
  }

  /**
   * 获取新客户列表
   */
  static getNewCustomersList() {
    console.log('\n🔍 获取新客户列表...');

    const uiXml = ADBHelper.dumpUI();
    const allTexts = UIHelper.extractAllTexts(uiXml);

    // 查找所有"查看"按钮
    const viewButtons = UIHelper.findAllElementsByText(uiXml, '查看');
    console.log(`找到 ${viewButtons.length} 个待处理的新客户`);

    return viewButtons;
  }

  /**
   * 提取客户名称
   */
  static extractCustomerName(uiXml) {
    // 方法1: 通过resource-id精确定位客户名称
    const regex1 = /resource-id="com\.tencent\.wework:id\/moj"[^>]*text="([^"]+)"/;
    const match1 = uiXml.match(regex1);
    if (match1 && match1[1]) {
      return match1[1];
    }

    // 方法2: 查找非系统文本的TextView
    const allTexts = UIHelper.extractAllTexts(uiXml);

    // 过滤掉系统文本和UI标签
    const systemTexts = [
      '通过验证', '发消息', '视频通话', '语音通话',
      '备注', '标签', '描述', '来源', '个人信息', '客户详情',
      '设置备注和描述', '设置标签', '微信', '企业微信',
      '添加', '删除', '确定', '取消', '返回', '添加时间',
      '对方通过扫一扫添加', '扫一扫'
    ];

    const customerTexts = allTexts.filter(text =>
      !systemTexts.includes(text) &&
      !text.includes('设置') &&
      !text.includes('添加') &&
      !text.includes('2025') && // 过滤日期
      !text.includes(':') && // 过滤时间
      text.length > 0 &&
      text.length < 50
    );

    // 第一个非系统文本通常是客户名称
    return customerTexts[0] || '未知客户';
  }

  /**
   * 通过好友验证
   */
  static approveCustomer(customerName) {
    console.log(`\n✅ 通过好友验证: ${customerName}`);

    // 验证当前在客户详情页
    const currentPage = this.detectCurrentPage();
    const activity = this.getCurrentActivity();
    console.log(`🔍 当前页面: ${currentPage}, Activity: ${activity}`);

    if (currentPage !== 'customer_detail') {
      console.error(`❌ 当前不在客户详情页,而是: ${currentPage}`);
      return false;
    }

    // Step 6: 点击通过验证
    const uiXml = ADBHelper.dumpUI();
    if (!UIHelper.elementExists(uiXml, '通过验证')) {
      console.error('❌ 未找到"通过验证"按钮');
      return false;
    }

    console.log('👆 点击通过验证...');
    const approveButton = coords.step5_customer_detail.elements['通过验证_button'];
    ADBHelper.tap(approveButton.center[0], approveButton.center[1], 3000); // 增加到3000ms

    // Step 7: 验证是否到达"通过验证后"页面(带重试)
    let afterPage = null;
    let afterActivity = null;
    for (let retry = 0; retry < 3; retry++) {
      ADBHelper.sleep(1000); // 每次等待1秒
      afterPage = this.detectCurrentPage();
      afterActivity = this.getCurrentActivity();
      console.log(`🔍 检查页面 (${retry + 1}/3): ${afterPage}, Activity: ${afterActivity}`);

      if (afterPage === 'after_approve') {
        break;
      }
    }

    if (afterPage !== 'after_approve') {
      console.error(`❌ 未到达"通过验证后"页面,当前: ${afterPage}`);
      return false;
    }

    // 验证并点击完成
    const uiXml2 = ADBHelper.dumpUI();
    if (!UIHelper.elementExists(uiXml2, '完成')) {
      console.error('❌ 未找到"完成"按钮');
      return false;
    }

    console.log('👆 点击完成...');
    const completeButton = coords.step6_after_approve.elements['完成_button'];
    ADBHelper.tap(completeButton.center[0], completeButton.center[1], 1500);

    // 等待页面跳转完成,最多等待5秒
    console.log('⏳ 等待页面跳转...');
    let jumpSuccess = false;
    for (let i = 0; i < 5; i++) {
      ADBHelper.sleep(1000);
      const currentPage = this.detectCurrentPage();
      const currentActivity = this.getCurrentActivity();
      console.log(`🔍 检查页面跳转 (${i + 1}/5): ${currentPage}, Activity: ${currentActivity}`);

      if (currentPage !== 'after_approve') {
        console.log('✅ 页面已跳转');
        jumpSuccess = true;
        break;
      }
    }

    if (!jumpSuccess) {
      console.error('❌ 页面跳转超时,仍在after_approve页面');
      return false;
    }

    console.log(`✅ 已通过验证: ${customerName}`);
    return true;
  }

  /**
   * 从点击"完成"后返回到消息页面
   * 智能判断当前页面,决定返回次数
   */
  static returnToMessagesAfterComplete() {
    console.log('\n🔙 智能返回到消息页面...');

    // 检查当前页面(点击"完成"后已经等待了3秒,页面应该已经跳转完成)
    const currentPage = this.detectCurrentPage();
    const currentActivity = this.getCurrentActivity();
    console.log(`🔍 点击"完成"后当前页面: ${currentPage}, Activity: ${currentActivity}`);

    if (currentPage === 'customer_detail') {
      // 情况1: 在客户详情页 → 需要2次返回
      console.log('📍 当前在客户详情页,需要点击2次返回');

      // 第1次返回: 客户详情页 → 新的客户页面
      console.log('⬅️  第1次返回: 客户详情页 → 新的客户页面...');
      ADBHelper.back(1500);

      const page1 = this.detectCurrentPage();
      console.log(`🔍 当前页面: ${page1}`);

      // 第2次返回: 新的客户页面 → 添加客户页面
      console.log('⬅️  第2次返回: 新的客户页面 → 添加客户页面...');
      ADBHelper.back(1500);

      const page2 = this.detectCurrentPage();
      console.log(`🔍 当前页面: ${page2}`);

    } else if (currentPage === 'new_customers_list') {
      // 情况2: 在"新的客户"页面 → 只需要1次返回
      console.log('📍 当前在"新的客户"页面,只需要点击1次返回');

      // 第1次返回: 新的客户页面 → 添加客户页面
      console.log('⬅️  第1次返回: 新的客户页面 → 添加客户页面...');
      ADBHelper.back(1500);

      const page1 = this.detectCurrentPage();
      console.log(`🔍 当前页面: ${page1}`);

    } else {
      console.error(`❌ 未知页面状态: ${currentPage}`);
    }

    // 点击底部"消息"标签
    console.log('👆 点击底部"消息"标签...');
    const messageTab = coords.step1_message_page.elements['消息'];
    ADBHelper.tap(messageTab.center[0], messageTab.center[1], 1500);

    const finalPage = this.detectCurrentPage();
    console.log(`🔍 当前页面: ${finalPage}`);

    if (finalPage === 'message_page') {
      console.log('✅ 成功到达消息页面');
      return true;
    } else {
      console.error(`❌ 未到达消息页面,当前: ${finalPage}`);
      return false;
    }
  }

  /**
   * 邀请客户到群聊 (混合版本 - 关键元素用文字查找,其他用验证过的坐标)
   */
  static inviteToGroup(customerName, groupName) {
    console.log(`\n👥 邀请客户到群聊: ${customerName} → ${groupName}`);

    try {
      // 加载邀请流程配置
      const inviteConfig = JSON.parse(fs.readFileSync(path.join(__dirname, 'invite_group_config.json'), 'utf8'));

      // Step 1: 确认当前在消息页面
      console.log('\n📍 Step 1: 确认当前在消息页面...');
      const currentActivity = this.getCurrentActivity();
      console.log(`🔍 当前Activity: ${currentActivity}`);

      if (currentActivity !== inviteConfig.activities.message_page) {
        console.error(`❌ 当前不在消息页面! Activity: ${currentActivity}`);
        return false;
      }

      // Step 2: 点击群聊入口 (文字查找)
      console.log(`\n📍 Step 2: 点击群聊"${groupName}"...`);
      ADBHelper.sleep(1000);
      let uiXml = ADBHelper.dumpUI();
      let element = UIHelper.findElementByText(uiXml, groupName);

      if (!element) {
        console.error(`❌ 未找到群聊: ${groupName}`);
        return false;
      }

      ADBHelper.tap(element.x, element.y, 1500);

      // 验证是否进入群聊页面
      const activity2 = this.getCurrentActivity();
      console.log(`🔍 当前Activity: ${activity2}`);
      if (activity2 !== inviteConfig.activities.group_chat) {
        console.error(`❌ 未进入群聊页面! Activity: ${activity2}`);
        return false;
      }
      console.log('✅ 已进入群聊页面');

      // Step 3: 点击右上角三个点 (使用配置文件中验证过的坐标)
      console.log('\n📍 Step 3: 点击右上角三个点...');
      const threeDotsButton = inviteConfig.steps.step2_group_chat.elements['三个点_button'];
      ADBHelper.tap(threeDotsButton.center[0], threeDotsButton.center[1], 1500);

      // 验证是否进入群详情页面
      const activity3 = this.getCurrentActivity();
      console.log(`🔍 当前Activity: ${activity3}`);
      if (activity3 !== inviteConfig.activities.group_details) {
        console.error(`❌ 未进入群详情页面! Activity: ${activity3}`);
        return false;
      }
      console.log('✅ 已进入群详情页面');

      // Step 4: 点击"查看全部群成员" (文字查找)
      console.log('\n📍 Step 4: 点击"查看全部群成员"...');
      uiXml = ADBHelper.dumpUI();
      element = UIHelper.findElementByText(uiXml, '查看全部群成员');
      if (!element) {
        console.error('❌ 未找到"查看全部群成员"按钮');
        return false;
      }
      ADBHelper.tap(element.x, element.y, 1500);

      // 验证是否进入全部群成员页面
      const activity4 = this.getCurrentActivity();
      console.log(`🔍 当前Activity: ${activity4}`);
      if (activity4 !== inviteConfig.activities.all_members) {
        console.error(`❌ 未进入全部群成员页面! Activity: ${activity4}`);
        return false;
      }
      console.log('✅ 已进入全部群成员页面');

      // Step 5: 点击"添加"按钮 (使用配置文件中验证过的坐标)
      console.log('\n📍 Step 5: 点击"添加"按钮...');
      const addButton = inviteConfig.steps.step4_all_members.elements['添加_button'];
      ADBHelper.tap(addButton.center[0], addButton.center[1], 1500);

      // 验证是否进入添加成员选择页面
      const activity5 = this.getCurrentActivity();
      console.log(`🔍 当前Activity: ${activity5}`);
      if (activity5 !== inviteConfig.activities.add_member_select) {
        console.error(`❌ 未进入添加成员选择页面! Activity: ${activity5}`);
        return false;
      }
      console.log('✅ 已进入添加成员选择页面');

      // Step 6: 点击"我的客户" (文字查找)
      console.log('\n📍 Step 6: 点击"我的客户"...');
      uiXml = ADBHelper.dumpUI();
      element = UIHelper.findElementByText(uiXml, '我的客户');
      if (!element) {
        console.error('❌ 未找到"我的客户"按钮');
        return false;
      }
      ADBHelper.tap(element.x, element.y, 1500);

      // 验证是否显示客户列表
      uiXml = ADBHelper.dumpUI();
      if (!UIHelper.elementExists(uiXml, customerName)) {
        console.error(`❌ 客户列表中未找到: ${customerName}`);
        return false;
      }
      console.log('✅ 已显示客户列表');

      // Step 7: 点击客户名称 (只在"今天"分组下查找 - 这是核心修复点!)
      console.log(`\n📍 Step 7: 点击客户"${customerName}"...`);

      // 找到"今天"分组的Y坐标范围
      const todayRegex = /text="今天"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/;
      const todayMatch = uiXml.match(todayRegex);

      if (!todayMatch) {
        console.error('❌ 未找到"今天"分组');
        return false;
      }

      const todayY2 = parseInt(todayMatch[4]); // "今天"标题的底部Y坐标
      console.log(`🔍 "今天"分组结束于Y=${todayY2}`);

      // 找到下一个分组(可能是"12-15"或其他日期)的Y坐标
      const nextGroupRegex = /text="12-15"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/;
      const nextGroupMatch = uiXml.match(nextGroupRegex);

      let nextGroupY1 = 9999; // 默认很大的值
      if (nextGroupMatch) {
        nextGroupY1 = parseInt(nextGroupMatch[2]); // 下一个分组的顶部Y坐标
        console.log(`🔍 下一个分组"12-15"开始于Y=${nextGroupY1}`);
      }

      // 查找所有"二进制刀仔"元素
      const customerRegex = new RegExp(`text="${customerName}"[^>]*bounds="\\[([0-9]+),([0-9]+)\\]\\[([0-9]+),([0-9]+)\\]"`, 'g');
      const customers = [];
      let match;

      while ((match = customerRegex.exec(uiXml)) !== null) {
        const x1 = parseInt(match[1]);
        const y1 = parseInt(match[2]);
        const x2 = parseInt(match[3]);
        const y2 = parseInt(match[4]);

        customers.push({
          x: Math.floor((x1 + x2) / 2),
          y: Math.floor((y1 + y2) / 2),
          y1: y1,
          bounds: `[${x1},${y1}][${x2},${y2}]`
        });
      }

      console.log(`🔍 找到 ${customers.length} 个"${customerName}"元素`);
      customers.forEach((c, i) => console.log(`  ${i + 1}. Y=${c.y1}, ${c.bounds}`));

      // 只选择Y坐标在"今天"分组范围内的客户
      const todayCustomers = customers.filter(c => c.y1 > todayY2 && c.y1 < nextGroupY1);

      if (todayCustomers.length === 0) {
        console.error(`❌ 在"今天"分组下未找到客户: ${customerName}`);
        return false;
      }

      element = todayCustomers[0];
      console.log(`✅ 选择"今天"分组下的客户: ${element.bounds}, 坐标: [${element.x}, ${element.y}]`);
      ADBHelper.tap(element.x, element.y);

      // 等待并验证客户是否被选中(检查是否出现"确定"按钮)
      console.log('⏳ 等待"确定"按钮出现...');
      let confirmButtonFound = false;
      for (let i = 0; i < 5; i++) {
        ADBHelper.sleep(1000);
        uiXml = ADBHelper.dumpUI();
        // 搜索"确定"开头的按钮(可能是"确定"或"确定(1)"等)
        if (uiXml.includes('text="确定')) {
          confirmButtonFound = true;
          break;
        }
        console.log(`  尝试 ${i + 1}/5: 未找到"确定"按钮,继续等待...`);
      }

      if (!confirmButtonFound) {
        console.error('❌ 客户未选中,未找到"确定"按钮');
        return false;
      }
      console.log('✅ 客户已选中');

      // Step 8: 点击"确定"按钮 (搜索"确定"开头的按钮,可能是"确定(1)"等)
      console.log('\n📍 Step 8: 点击"确定"按钮...');

      // 使用正则表达式查找"确定"开头的按钮
      const confirmRegex = /text="确定[^"]*"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/;
      const confirmMatch = uiXml.match(confirmRegex);

      if (!confirmMatch) {
        console.error('❌ 未找到"确定"按钮');
        return false;
      }

      const x1 = parseInt(confirmMatch[1]);
      const y1 = parseInt(confirmMatch[2]);
      const x2 = parseInt(confirmMatch[3]);
      const y2 = parseInt(confirmMatch[4]);
      const confirmX = Math.floor((x1 + x2) / 2);
      const confirmY = Math.floor((y1 + y2) / 2);

      console.log(`✅ 找到"确定"按钮, 坐标: [${confirmX}, ${confirmY}]`);
      ADBHelper.tap(confirmX, confirmY, 2000);

      // 验证是否完成邀请
      const activity8 = this.getCurrentActivity();
      console.log(`🔍 当前Activity: ${activity8}`);

      if (activity8 === inviteConfig.activities.all_members || activity8 === inviteConfig.activities.group_details) {
        console.log(`✅ 成功邀请客户到群聊: ${customerName} → ${groupName}`);
        return true;
      } else {
        console.error(`❌ 邀请后页面异常! Activity: ${activity8}`);
        return false;
      }

    } catch (error) {
      console.error('❌ 邀请客户到群聊失败:', error.message);
      return false;
    }
  }

  /**
   * 批量通过所有好友申请
   * @returns {string[]} - 返回所有通过验证的客户名称数组
   */
  static approveAllCustomers() {
    console.log('\n📋 批量通过所有好友申请...\n');

    const approvedCustomers = [];
    let processedCount = 0;

    // 循环处理所有新客户,直到没有"查看"按钮为止
    while (true) {
      // Dump UI检查是否还有"查看"按钮
      const uiXml = ADBHelper.dumpUI();

      // 查找所有"查看"按钮
      const viewButtons = [];
      const viewRegex = /text="查看"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/g;
      let match;

      while ((match = viewRegex.exec(uiXml)) !== null) {
        const x1 = parseInt(match[1]);
        const y1 = parseInt(match[2]);
        const x2 = parseInt(match[3]);
        const y2 = parseInt(match[4]);

        viewButtons.push({
          x: Math.floor((x1 + x2) / 2),
          y: Math.floor((y1 + y2) / 2)
        });
      }

      // 如果没有"查看"按钮,说明所有客户都已处理完毕
      if (viewButtons.length === 0) {
        console.log('✅ 所有好友申请已处理完毕\n');
        break;
      }

      processedCount++;
      console.log(`\n🎯 处理第 ${processedCount} 个好友申请 (剩余 ${viewButtons.length} 个)...`);

      // 点击第一个"查看"按钮
      const viewButton = viewButtons[0];
      console.log(`👆 点击"查看"按钮: [${viewButton.x}, ${viewButton.y}]`);
      ADBHelper.tap(viewButton.x, viewButton.y, 1500);

      // 获取客户名称
      const detailUiXml = ADBHelper.dumpUI();
      const nameRegex = /text="([^"]+)"[^>]*resource-id="com\.tencent\.wework:id\/moj"/;
      const nameMatch = detailUiXml.match(nameRegex);

      if (!nameMatch) {
        console.error('❌ 无法获取客户名称,跳过此客户');
        ADBHelper.back(1000);
        continue;
      }

      const customerName = nameMatch[1];
      console.log(`📝 客户名称: ${customerName}`);

      // 点击"通过验证"按钮
      console.log('👆 点击"通过验证"...');
      const approveButton = UIHelper.findElementByText(detailUiXml, '通过验证');
      if (!approveButton) {
        console.error('❌ 未找到"通过验证"按钮,跳过此客户');
        ADBHelper.back(1000);
        continue;
      }

      ADBHelper.tap(approveButton.x, approveButton.y, 1500);

      // 点击"完成"按钮
      console.log('👆 点击"完成"...');
      const completeUiXml = ADBHelper.dumpUI();
      const completeButton = UIHelper.findElementByText(completeUiXml, '完成');
      if (!completeButton) {
        console.error('❌ 未找到"完成"按钮,跳过此客户');
        ADBHelper.back(2000);
        continue;
      }

      ADBHelper.tap(completeButton.x, completeButton.y, 2000);

      // 等待页面跳转,可能跳转到客户详情页或"新的客户"列表
      ADBHelper.sleep(1500);

      // 检查当前页面 - 使用UI元素检测而不是Activity
      const checkUiXml = ADBHelper.dumpUI();

      // 检查是否有"查看"按钮(说明在"新的客户"列表)
      const hasViewButton = checkUiXml.includes('text="查看"');

      if (!hasViewButton) {
        console.log('⬅️  从好友详情页返回到"新的客户"列表...');
        ADBHelper.back(2000); // 返回到"新的客户"列表
      } else {
        console.log('✅ 已在"新的客户"列表页面');
      }

      // 记录客户名称
      approvedCustomers.push(customerName);
      console.log(`✅ 已通过验证: ${customerName}`);

      // 额外等待,确保页面完全刷新
      ADBHelper.sleep(1000);
    }

    console.log(`\n📊 批量通过完成! 共通过 ${approvedCustomers.length} 个好友申请:`);
    approvedCustomers.forEach((name, i) => console.log(`  ${i + 1}. ${name}`));

    return approvedCustomers;
  }

  /**
   * 批量邀请客户到群聊
   * @param {string[]} customerNames - 客户名称数组
   * @param {string} groupName - 群聊名称
   * @returns {Object} - 返回成功和失败的统计
   */
  static inviteAllToGroup(customerNames, groupName) {
    console.log(`\n📋 批量邀请 ${customerNames.length} 个客户到群聊: ${groupName}\n`);

    if (customerNames.length === 0) {
      console.log('⚠️  没有客户需要邀请');
      return { success: [], failed: [] };
    }

    // 去重客户名称,避免重复勾选导致取消勾选
    const uniqueCustomerNames = [...new Set(customerNames)];
    if (uniqueCustomerNames.length < customerNames.length) {
      console.log(`⚠️  检测到重复的客户名称,已去重: ${customerNames.length} → ${uniqueCustomerNames.length}`);
      customerNames = uniqueCustomerNames;
    }

    try {
      // Step 1: 从"新的客户"列表返回到"通讯录"页面,然后进入消息页面
      console.log('📍 Step 1: 返回到"通讯录"页面...');

      // 从"新的客户"列表返回到"通讯录"页面
      ADBHelper.back(1500);

      // 检查是否在"通讯录"页面
      let uiXml = ADBHelper.dumpUI();
      const hasContactsTab = uiXml.includes('text="通讯录"');

      if (!hasContactsTab) {
        console.error('❌ 未能返回到"通讯录"页面');
        return { success: [], failed: customerNames };
      }
      console.log('✅ 已返回到"通讯录"页面\n');

      // 点击底部"消息"标签
      console.log('📍 Step 2: 点击底部"消息"标签...');
      ADBHelper.tap(72, 1582, 1500);

      // 检查是否在消息页面
      uiXml = ADBHelper.dumpUI();
      const hasMessagePage = uiXml.includes('text="消息"');

      if (!hasMessagePage) {
        console.error('❌ 未能进入消息页面');
        return { success: [], failed: customerNames };
      }
      console.log('✅ 已进入消息页面\n');

      // Step 3: 点击群聊
      console.log(`📍 Step 3: 点击群聊"${groupName}"...`);
      uiXml = ADBHelper.dumpUI();
      const groupElement = UIHelper.findElementByText(uiXml, groupName);
      if (!groupElement) {
        console.error(`❌ 未找到群聊: ${groupName}`);
        return { success: [], failed: customerNames };
      }
      ADBHelper.tap(groupElement.x, groupElement.y, 1500);
      console.log('✅ 已进入群聊页面\n');

      // Step 4: 点击右上角三个点
      console.log('📍 Step 4: 点击右上角三个点...');
      ADBHelper.tap(682, 124, 1500);
      console.log('✅ 已进入群详情页面\n');

      // Step 5: 点击"查看全部群成员"
      console.log('📍 Step 5: 点击"查看全部群成员"...');
      uiXml = ADBHelper.dumpUI();
      const memberElement = UIHelper.findElementByText(uiXml, '查看全部群成员');
      if (!memberElement) {
        console.error('❌ 未找到"查看全部群成员"');
        return { success: [], failed: customerNames };
      }
      ADBHelper.tap(memberElement.x, memberElement.y, 1500);
      console.log('✅ 已进入全部群成员页面\n');

      // Step 6: 点击"添加"按钮
      console.log('📍 Step 6: 点击"添加"按钮...');
      ADBHelper.tap(654, 124, 1500);
      console.log('✅ 已进入添加成员选择页面\n');

      // Step 7: 点击"我的客户"
      console.log('📍 Step 7: 点击"我的客户"...');
      uiXml = ADBHelper.dumpUI();
      const myCustomerElement = UIHelper.findElementByText(uiXml, '我的客户');
      if (!myCustomerElement) {
        console.error('❌ 未找到"我的客户"');
        return { success: [], failed: customerNames };
      }
      ADBHelper.tap(myCustomerElement.x, myCustomerElement.y, 1500);
      uiXml = ADBHelper.dumpUI();
      console.log('✅ 已显示客户列表\n');

      // Step 8: 逐个勾选所有需要邀请的客户
      console.log(`📍 Step 8: 勾选 ${customerNames.length} 个客户...\n`);

      const selectedCustomers = [];
      const failedCustomers = [];

      for (let i = 0; i < customerNames.length; i++) {
        const customerName = customerNames[i];
        console.log(`  ${i + 1}/${customerNames.length}. 勾选客户: ${customerName}`);

        // 查找"今天"分组下的客户
        const todayRegex = /text="今天"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/;
        const todayMatch = uiXml.match(todayRegex);

        if (!todayMatch) {
          console.error(`  ❌ 未找到"今天"分组`);
          failedCustomers.push(customerName);
          continue;
        }

        const todayY2 = parseInt(todayMatch[4]);

        // 查找下一个分组
        const nextGroupRegex = /text="12-15"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/;
        const nextGroupMatch = uiXml.match(nextGroupRegex);
        const nextGroupY1 = nextGroupMatch ? parseInt(nextGroupMatch[2]) : 9999;

        // 查找所有匹配的客户
        const customerRegex = new RegExp(`text="${customerName}"[^>]*bounds="\\[([0-9]+),([0-9]+)\\]\\[([0-9]+),([0-9]+)\\]"`, 'g');
        const customers = [];
        let match;

        while ((match = customerRegex.exec(uiXml)) !== null) {
          const x1 = parseInt(match[1]);
          const y1 = parseInt(match[2]);
          const x2 = parseInt(match[3]);
          const y2 = parseInt(match[4]);

          customers.push({
            x: Math.floor((x1 + x2) / 2),
            y: Math.floor((y1 + y2) / 2),
            y1: y1
          });
        }

        // 只选择"今天"分组下的客户
        const todayCustomers = customers.filter(c => c.y1 > todayY2 && c.y1 < nextGroupY1);

        if (todayCustomers.length === 0) {
          console.error(`  ❌ 在"今天"分组下未找到客户: ${customerName}`);
          failedCustomers.push(customerName);
          continue;
        }

        // 检查客户是否已经在群里(通过检查ImageView的enabled属性)
        const customer = todayCustomers[0];

        // 查找客户对应的ImageView,检查enabled状态
        const imageViewPattern = new RegExp(
          `resource-id="com\\.tencent\\.wework:id/lmb"[^>]*bounds="\\[[0-9]+,${customer.y1 - 50}\\]\\[[0-9]+,${customer.y1 + 100}\\]"[^>]*enabled="(true|false)"`,
          'i'
        );
        const imageViewMatch = uiXml.match(imageViewPattern);

        if (imageViewMatch && imageViewMatch[1] === 'false') {
          console.log(`  ⚠️  客户已在群里,跳过: ${customerName}`);
          failedCustomers.push(customerName);
          continue;
        }

        // 点击勾选客户
        ADBHelper.tap(customer.x, customer.y, 500); // 减少等待时间,加快勾选速度
        console.log(`  ✅ 已勾选: ${customerName}`);
        selectedCustomers.push(customerName);

        // 不要重新dump UI,避免重复点击导致取消勾选!
      }

      console.log(`\n✅ 已勾选 ${selectedCustomers.length} 个客户\n`);

      // 如果没有成功勾选任何客户,直接返回
      if (selectedCustomers.length === 0) {
        console.log('⚠️  没有客户被成功勾选(可能都已在群里),跳过邀请步骤');
        return {
          success: [],
          failed: failedCustomers
        };
      }

      // Step 9: 点击"确定"按钮
      console.log('📍 Step 9: 点击"确定"按钮...');

      // 等待"确定"按钮出现
      ADBHelper.sleep(1000);
      uiXml = ADBHelper.dumpUI();

      // 尝试通过text查找"确定"按钮
      let confirmRegex = /text="确定[^"]*"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/;
      let confirmMatch = uiXml.match(confirmRegex);

      // 如果通过text找不到,尝试通过resource-id查找(右上角第二个按钮)
      if (!confirmMatch) {
        console.log('ℹ️  通过text未找到"确定"按钮,尝试通过resource-id查找...');
        confirmRegex = /resource-id="com\.tencent\.wework:id\/nhn"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/;
        confirmMatch = uiXml.match(confirmRegex);
      }

      if (!confirmMatch) {
        console.error('❌ 未找到"确定"按钮');
        return { success: [], failed: customerNames };
      }

      const x1 = parseInt(confirmMatch[1]);
      const y1 = parseInt(confirmMatch[2]);
      const x2 = parseInt(confirmMatch[3]);
      const y2 = parseInt(confirmMatch[4]);
      const confirmX = Math.floor((x1 + x2) / 2);
      const confirmY = Math.floor((y1 + y2) / 2);

      console.log(`✅ 找到"确定"按钮, 坐标: [${confirmX}, ${confirmY}]`);
      ADBHelper.tap(confirmX, confirmY, 2000);

      // Step 10: 智能判断是否有"邀请"确认弹窗
      console.log('\n📍 Step 10: 检查是否有"邀请"确认弹窗...');

      // 等待可能的弹窗出现
      ADBHelper.sleep(1000);
      uiXml = ADBHelper.dumpUI();

      // 查找"邀请"按钮
      const inviteButtonRegex = /text="邀请"[^>]*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/;
      const inviteButtonMatch = uiXml.match(inviteButtonRegex);

      if (inviteButtonMatch) {
        console.log('✅ 检测到"邀请"确认弹窗,点击"邀请"按钮...');

        const inviteX1 = parseInt(inviteButtonMatch[1]);
        const inviteY1 = parseInt(inviteButtonMatch[2]);
        const inviteX2 = parseInt(inviteButtonMatch[3]);
        const inviteY2 = parseInt(inviteButtonMatch[4]);
        const inviteX = Math.floor((inviteX1 + inviteX2) / 2);
        const inviteY = Math.floor((inviteY1 + inviteY2) / 2);

        console.log(`✅ 找到"邀请"按钮, 坐标: [${inviteX}, ${inviteY}]`);
        ADBHelper.tap(inviteX, inviteY, 2000);
        console.log('✅ 已点击"邀请"按钮');
      } else {
        console.log('ℹ️  未检测到"邀请"确认弹窗,直接完成');
      }

      console.log(`\n✅ 成功邀请 ${selectedCustomers.length} 个客户到群聊!\n`);

      return {
        success: selectedCustomers,
        failed: failedCustomers
      };

    } catch (error) {
      console.error('❌ 批量邀请失败:', error.message);
      return { success: [], failed: customerNames };
    }
  }

  /**
   * 处理单个新客户
   */
  static processNewCustomer(viewButtonIndex = 0) {
    console.log(`\n\n🎯 开始处理新客户 #${viewButtonIndex + 1}...`);

    try {
      // 获取当前UI
      const uiXml = ADBHelper.dumpUI();
      const viewButtons = UIHelper.findAllElementsByText(uiXml, '查看');

      if (viewButtonIndex >= viewButtons.length) {
        console.log('⚠️  没有更多新客户');
        return null;
      }

      // Step 4: 点击查看按钮
      const viewButton = viewButtons[viewButtonIndex];
      ADBHelper.tap(viewButton.x, viewButton.y);

      // Step 5: 提取客户名称
      ADBHelper.sleep(1500);
      const detailUiXml = ADBHelper.dumpUI();
      const customerName = this.extractCustomerName(detailUiXml);
      console.log(`📝 客户名称: ${customerName}`);

      // 检查是否已处理
      if (DataManager.isCustomerProcessed(customerName)) {
        console.log(`⚠️  客户已处理过,跳过: ${customerName}`);
        // 返回到"新的客户"列表
        ADBHelper.back();
        return null;
      }

      // 通过验证
      const approveSuccess = this.approveCustomer(customerName);

      if (!approveSuccess) {
        console.error(`❌ 通过验证失败: ${customerName}`);
        // 返回到"新的客户"列表
        ADBHelper.back();
        return null;
      }

      // 保存客户记录
      DataManager.saveCustomer({ name: customerName });

      // 点击"完成"后,智能返回到消息页面
      this.returnToMessagesAfterComplete();

      // 邀请到群聊
      const config = DataManager.loadConfig();
      const success = this.inviteToGroup(customerName, config.targetGroup);

      if (success) {
        // 更新邀请状态
        DataManager.updateCustomerInvited(customerName);
      }

      console.log(`\n✅ 客户处理完成: ${customerName}`);
      return customerName;

    } catch (error) {
      console.error('❌ 处理客户时出错:', error.message);
      return null;
    }
  }
}

module.exports = {
  ADBHelper,
  UIHelper,
  DataManager,
  WeworkAutomation,
  coords
};
