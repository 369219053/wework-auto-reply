# 企业微信自动化 - 批量通过好友申请并邀请进群 (Android版)

## 📖 项目简介

本项目是基于Android AccessibilityService的企业微信自动化应用,实现批量自动化操作:

### 功能一: 批量通过好友申请并邀请进群
- 🤖 **批量通过好友申请** - 一次性处理所有待处理的好友申请
- ✅ **批量邀请进群** - 一次性邀请所有新添加的客户到指定群聊

### 功能二: 批量发送消息 (最新)
- 📤 **批量转发消息** - 从素材库聊天转发消息到多个群聊/联系人
- 🎯 **智能滚动查找** - 自动滚动查找不可见的联系人
- 🔤 **全角/半角智能匹配** - 自动处理字符编码差异
- ⏱️ **随机延迟** - 支持设置随机延迟,模拟真人操作

### 通用特性
- 📱 **原生Android应用** - 无需电脑,手机独立运行
- 🎯 **一键执行** - 点击按钮即可完成所有操作
- 🔧 **智能识别** - 自动识别UI元素,适配不同版本

## ✨ 核心功能

### 批量处理流程

**阶段1: 批量通过好友申请**
```
检测"新的客户"列表 → 循环处理所有好友 → 点击"查看" → 点击"通过验证" → 点击"完成" → 返回列表 → 继续下一个 → 记录所有客户名称
```

**阶段2: 批量邀请到群聊**
```
返回消息页面 → 使用搜索功能找到群聊 → 进入群聊 → 点击右上角三个点 → 点击+号 → 我的客户 → 勾选所有刚通过的客户 → 点击确定 → 完成邀请
```

### 智能特性

- ✅ **批量处理** - 一次性处理所有好友申请,无需逐个操作
- ✅ **智能检测** - 基于UI元素检测页面状态,准确可靠
- ✅ **精准定位** - 只在"今天"分组下查找客户,避免误操作
- ✅ **完整的错误处理** - 自动重试和错误恢复机制
- ✅ **详细的操作日志** - 每一步操作都有清晰的日志记录

## 🛠️ 技术栈

### Android应用 (当前版本)
- **Kotlin** - 主要开发语言
- **Android AccessibilityService** - 无障碍服务,实现UI自动化
- **SharedPreferences** - 应用间通信
- **GestureDescription** - 手势模拟,实现精准点击

### Node.js版本 (已废弃)
- **Node.js** - 主要开发语言
- **ADB** - Android调试桥,用于控制手机
- **UIAutomator** - 获取UI元素信息

## 🚀 快速开始

### 环境要求

1. **Android手机** (Android 7.0+)
2. **企业微信** (已登录)
3. **开发环境** (仅用于编译,可选):
   - Android Studio
   - JDK 11+
   - Gradle

### 安装步骤

#### 方式1: 直接安装APK (推荐)

1. **下载APK**
   - 从 `app/build/outputs/apk/debug/app-debug.apk` 获取最新版本

2. **安装应用**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   或直接在手机上安装APK文件

3. **开启无障碍服务**
   - 打开手机"设置" → "无障碍" → "企微自动回复"
   - 开启服务权限

4. **使用应用**
   - 打开"企微自动回复"应用
   - 输入目标群聊名称(例如: `智界Aigc客户群`)
   - 点击"开始批量处理"
   - 应用会自动打开企业微信并执行自动化流程

#### 方式2: 从源码编译

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd wework-auto-reply
   ```

2. **编译APK**
   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew assembleDebug
   ```

3. **安装到手机**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### 使用说明

1. **启动应用**
   - 打开"企微自动回复"应用
   - 确保无障碍服务已开启

2. **输入群聊名称**
   - 在输入框中输入目标群聊名称
   - 例如: `智界Aigc客户群`
   - 注意: 只需输入群聊名称,不需要包含人数

3. **开始批量处理**
   - 点击"开始批量处理"按钮
   - 应用会自动最小化并打开企业微信
   - 自动执行完整流程:
     - 批量通过所有好友申请
     - 批量邀请所有新客户到群聊

4. **查看日志**
   - 应用会显示实时操作日志
   - 可通过 `adb logcat` 查看详细调试信息:
     ```bash
     adb logcat | grep "WEWORK_DEBUG"
     ```

## 📋 配置说明

### 应用配置

应用使用SharedPreferences存储配置,主要配置项:

| 配置项 | 说明 | 存储位置 |
|--------|------|---------|
| target_group_name | 目标群聊名称 | SharedPreferences |
| should_start_batch | 是否开始批量处理 | SharedPreferences |
| start_time | 开始时间戳 | SharedPreferences |

**重要:**
- 群聊名称只需输入主要部分,例如 `智界Aigc客户群`
- 应用会自动匹配包含该名称的群聊(包括人数)

## 📚 详细文档

### 功能一相关
- [UI结构文档](./项目文档整理/UI结构文档.md)
- [调试指南](./项目文档整理/调试指南.md)

### 功能二相关
- [批量发送功能 - 语音输入问题解决](./项目文档整理/批量发送功能-语音输入问题解决.md)
- [批量发送功能 - 常见问题与解决方法](./项目文档整理/批量发送功能-常见问题与解决方法.md)

## 🎯 核心代码说明

### WeworkAutoService.kt

**主要组件:**
- `WeworkAutoService` - AccessibilityService核心服务
- `MainActivity` - 应用主界面

**核心方法:**

1. **`startBatchProcess()`** - 启动批量处理
   - 保存配置到SharedPreferences
   - 启动企业微信应用
   - 触发自动化流程

2. **`navigateToContacts()`** - 导航到通讯录
   - 查找并点击"通讯录"按钮
   - 使用智能点击策略(直接点击 → 父节点点击 → 手势点击)

3. **`openNewCustomers()`** - 打开新客户列表
   - 查找并点击"添加客户"按钮
   - 智能等待页面加载完成

4. **`processNextCustomer()`** - 处理下一个客户
   - 查找"查看"按钮
   - 点击"通过验证"
   - 点击"完成"
   - 智能等待加载完成
   - 返回列表继续处理

5. **`navigateToMessages()`** - 导航到消息页面
   - 智能检测当前页面状态
   - 自动返回到主页面
   - 点击"消息"标签

6. **`openGroupChat()`** - 打开群聊 (使用搜索功能)
   - 点击放大镜按钮打开搜索页面
   - 输入群聊名称到搜索框
   - 在搜索结果中查找并点击目标群聊
   - 无需滚动消息列表,直接搜索定位

7. **`openGroupMembers()`** - 打开群成员列表
   - 查找右上角"..."按钮 (resource-id: `com.tencent.wework:id/nhi`)
   - 点击菜单按钮

8. **`clickNode()`** - 智能点击节点
   - 三层点击策略:
     1. 直接点击节点
     2. 点击可点击的父节点
     3. 使用GestureDescription手势点击

### 关键技术点

1. **智能UI元素查找**
   - `findNodeByText()` - 通过文本查找
   - `findNodeByTextExact()` - 精确文本匹配
   - `findNodeByResourceId()` - 通过resource-id查找
   - `findNodeContainingText()` - 模糊文本匹配
   - `findAllNodesByText()` - 查找所有匹配节点

2. **智能点击策略**
   - 直接点击 → 父节点点击 → 手势点击
   - 使用GestureDescription实现精准点击
   - 自动获取节点屏幕坐标

3. **智能等待机制**
   - `waitForLoadingComplete()` - 等待加载完成
   - 检测加载指示器
   - 检测页面状态变化
   - 最多等待10秒,避免无限等待

4. **状态机管理**
   - 使用ProcessState枚举管理流程状态
   - 每个状态对应一个处理方法
   - 自动状态转换和错误恢复

5. **点击"我的客户"分类标签的正确方法** ⭐
   - ❌ **错误方法**: 直接点击"我的客户"文本节点或使用坐标点击
   - ✅ **正确方法**: 点击"我的客户"分类标签的头像节点
   - **实现步骤**:
     1. 使用`findNodeByTextExact()`查找"我的客户"文本节点
     2. 向上遍历父节点,找到`resource-id="com.tencent.wework:id/cmd"`的父节点
     3. 在该父节点下查找`resource-id="com.tencent.wework:id/lmb"`的头像节点
     4. 使用`clickNode()`点击头像节点(有三层fallback机制)
   - **关键原因**:
     - 文本节点不可点击(`clickable="false"`)
     - 坐标点击在某些设备上不稳定
     - 头像节点是明确可点击的UI元素(`clickable="true"`)

6. **智能页面状态检测**
   - 点击"添加"按钮后,先检查是否已在"我的客户"页面
   - 通过查找"根据标签筛选"文本判断页面状态
   - 如果已在目标页面,直接执行下一步
   - 如果不在,点击"我的客户"分类标签并等待页面切换
   - 点击后等待3秒,再次检查页面状态
   - 如果页面未切换,自动重试

## 🔧 使用说明

### 批量处理流程

**步骤1: 准备工作**
1. 确保企业微信已登录
2. 修改 `config.json` 中的群聊名称
3. 连接手机: `adb connect 192.168.31.xxx:5555`

**步骤2: 手动导航**
1. 打开企业微信
2. 点击底部"通讯录"标签
3. 点击"添加客户"
4. 点击"新的客户"
5. 停在"新的客户"列表页面

**步骤3: 执行批量处理**
```bash
node test_batch_process.js
```

**步骤4: 查看结果**
- 脚本会自动完成所有操作
- 查看控制台日志了解处理进度
- 所有操作都会记录在 `customers.json`

### 单独测试功能

**只测试批量通过好友申请:**
```bash
# 手动操作到"新的客户"列表页面
node test_batch_process.js
# 脚本会停在批量通过完成后
```

**只测试批量邀请:**
```bash
# 修改 test_invite_batch.js 中的客户名称
node test_invite_batch.js
```

## ⚠️ 注意事项

1. **手机保持唤醒** - 确保屏幕不会自动锁定
2. **企业微信保持前台** - 不要切换到其他应用
3. **网络稳定** - 确保手机网络连接正常
4. **群聊名称准确** - 必须完全匹配,包括括号和数字
5. **手动导航到起始页面** - 脚本执行前需要手动操作到"新的客户"列表
6. **客户在"今天"分组** - 只会处理"今天"分组下的客户

## 🐛 常见问题

### 1. 双企微弹窗无法自动点击 / 无法打开企微

**问题描述:**
- 点击"开始批量发送"后,弹出双企微选择弹窗,但是没有自动点击
- 应用最小化返回桌面,企微没有打开
- 或者有时能打开,有时打不开,非常不稳定

**根本原因:**
当前架构中,**只有WeworkAutoService负责处理双企微弹窗**,BatchSendService不处理弹窗:

1. **WeworkAutoService配置:**
   - 监听所有应用事件(没有`packageNames`限制)
   - 检测到`com.vivo.doubleinstance`弹窗时,检查`shouldStartAuto`和`shouldStartBatch`
   - 如果其中一个为true,使用**坐标点击**(`clickWeworkByCoordinate`)点击弹窗
   - 功能一和功能二都通过WeworkAutoService点击弹窗

2. **BatchSendService配置:**
   - **只监听企微事件**(`packageNames="com.tencent.wework"`)
   - **收不到双企微弹窗事件**(`com.vivo.doubleinstance`)
   - 虽然有处理弹窗的代码,但永远不会被调用

**错误的修改方式(会导致无法打开企微):**

❌ **错误1: 移除BatchSendService的packageNames限制**
```xml
<!-- 错误:让BatchSendService监听所有事件 -->
<accessibility-service
    android:packageNames="" />  <!-- 或者完全移除这一行 -->
```
这会导致BatchSendService也收到弹窗事件,与WeworkAutoService冲突。

❌ **错误2: 让WeworkAutoService在功能二启动时不处理弹窗**
```kotlin
// 错误:让WeworkAutoService跳过功能二的弹窗
if (shouldStartBatch) {
    return  // 不处理弹窗
}
```
这会导致没有Service处理弹窗,或者只有BatchSendService处理,但BatchSendService使用resource-id查找节点,可能找不到。

❌ **错误3: BatchSendService使用resource-id查找节点**
```kotlin
// 错误:使用resource-id查找,可能找不到节点
val targetResourceId = "com.vivo.doubleinstance:id/second"
val targetNode = findNodeByResourceIdRecursive(rootNode, targetResourceId)
```
不同手机的双企微弹窗resource-id可能不同,使用坐标点击更可靠。

**正确的架构(当前版本):**
- ✅ **只有WeworkAutoService处理双企微弹窗**
- ✅ **WeworkAutoService使用坐标点击,不依赖resource-id**
- ✅ **BatchSendService只处理企微内的事件**
- ✅ **功能一和功能二都通过WeworkAutoService点击弹窗**

**解决方案:**
如果遇到无法打开企微的问题,请恢复到GitHub上的最新版本:
```bash
git restore app/src/main/java/com/wework/autoreply/BatchSendService.kt
git restore app/src/main/java/com/wework/autoreply/WeworkAutoService.kt
git restore app/src/main/res/xml/batch_send_service_config.xml
```

### 2. ADB连接失败
```bash
# 重启ADB服务
adb kill-server
adb start-server

# 无线连接
adb connect 192.168.31.xxx:5555

# 检查连接
adb devices
```

### 3. 找不到"查看"按钮
- 确认在"新的客户"列表页面
- 确认有待处理的好友申请
- 检查UI dump: `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml`

### 4. 客户未选中
- 确认客户在"今天"分组下
- 检查客户名称是否完全匹配
- 查看日志中的Y坐标范围

### 5. 邀请失败
- 检查群聊名称是否**完全匹配**(包括括号和数字)
- 确认客户已经通过好友验证
- 查看 `customers.json` 确认记录

### 6. 返回页面错误
- 脚本使用UI元素检测页面状态
- 如果检测失败,手动返回到正确页面
- 查看日志中的页面检测信息

### 7. 点击放大镜按钮失败

**问题描述:**
- 在"我的客户"页面,脚本无法点击右上角的搜索按钮(放大镜🔍)
- 使用GestureDescription点击坐标没有反应
- 企业微信可能屏蔽了AccessibilityService的手势点击

**根本原因:**
企业微信的某些按钮(如放大镜按钮)被标记为`NAF="true"`(Not Accessibility Friendly),导致:
1. **UI dump看不到** - `uiautomator dump`无法获取这些按钮的信息
2. **GestureDescription点击无效** - 企业微信屏蔽了手势点击事件
3. **坐标点击失败** - 虽然坐标正确,但点击不生效

**解决方案:**
使用**节点遍历 + 坐标匹配**的方法:

```kotlin
/**
 * 根据坐标查找节点并点击
 */
private fun findNodeByCoordinates(
    node: AccessibilityNodeInfo,
    targetX: Int,
    targetY: Int,
    tolerance: Int
): AccessibilityNodeInfo? {
    // 获取节点的屏幕坐标
    val rect = android.graphics.Rect()
    node.getBoundsInScreen(rect)

    // 计算节点中心点
    val centerX = (rect.left + rect.right) / 2
    val centerY = (rect.top + rect.bottom) / 2

    // 检查是否在目标坐标附近
    if (Math.abs(centerX - targetX) <= tolerance &&
        Math.abs(centerY - targetY) <= tolerance) {
        // 检查节点是否可点击
        if (node.isClickable ||
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
            return node
        }
    }

    // 递归查找子节点
    for (i in 0 until node.childCount) {
        val child = node.getChild(i)
        if (child != null) {
            val result = findNodeByCoordinates(child, targetX, targetY, tolerance)
            if (result != null) {
                return result
            }
        }
    }

    return null
}
```

**使用方法:**
```kotlin
// 1. 计算目标坐标(使用相对坐标适配不同分辨率)
val screenWidth = resources.displayMetrics.widthPixels
val searchButtonX = screenWidth - 130  // 720px屏幕上为590
val searchButtonY = 124

// 2. 查找坐标附近的可点击节点
val rootNode = rootInActiveWindow
val targetNode = findNodeByCoordinates(rootNode, searchButtonX, searchButtonY, 50)

// 3. 使用performAction点击节点
if (targetNode != null) {
    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
}
```

**优势:**
- ✅ **不依赖UI dump** - 运行时遍历所有节点,包括NAF=true的节点
- ✅ **不会被屏蔽** - 使用performAction点击,符合Accessibility规范
- ✅ **适配不同分辨率** - 使用相对坐标计算,自动适配不同屏幕

**测试结果:**
- 720px屏幕: 搜索按钮坐标为(590, 124),成功点击 ✅
- 使用相对坐标: `x = screenWidth - 130`,适配所有分辨率 ✅

## 📅 更新日志

### V3.3 (2025-12-25) - 批量邀请功能完成

**已完成:**
- ✅ **批量邀请客户进群** - 通过搜索功能逐个邀请客户进群
- ✅ **节点遍历 + 坐标匹配** - 解决企业微信NAF按钮无法点击的问题
- ✅ **放大镜按钮点击** - 成功点击"我的客户"页面的搜索按钮
- ✅ **相对坐标适配** - 使用`screenWidth - 130`自动适配不同分辨率

**技术要点:**
- **节点遍历**: 运行时遍历所有节点,包括NAF=true的节点
- **坐标匹配**: 计算节点中心点,匹配目标坐标(容差50px)
- **performAction点击**: 使用Accessibility规范的点击方式,不会被屏蔽
- **相对坐标**: `x = screenWidth - 130, y = 124`,适配所有分辨率

### V3.2 (2025-12-24) - 搜索功能优化

**已完成:**
- ✅ **搜索功能集成** - 使用搜索功能查找群聊,无需滚动消息列表
- ✅ **+号按钮点击优化** - 改为点击群详情页面的+号按钮,避免弹窗问题
- ✅ **智能ViewGroup查找** - 在搜索结果中准确定位可点击的群聊项
- ✅ **删除测试模式** - 清理所有测试代码,简化代码结构

**技术要点:**
- **搜索流程**: 点击放大镜 → 输入群聊名称 → 查找搜索结果 → 点击群聊
- **+号点击**: 在群详情页面的RecyclerView中查找倒数第二个ImageView
- **ViewGroup查找**: 递归查找所有可点击的ViewGroup,检查是否包含目标文本
- **EditText查找**: 递归查找EditText节点,用于输入搜索文本

### V3.1 (2025-12-21) - 双企微支持 + 智能导航优化

**已完成:**
- ✅ **双APK架构** - 使用Product Flavors创建APK1(企微1)和APK2(企微2)
- ✅ **独立数据库** - 每个APK有独立的数据库和SharedPreferences
- ✅ **消息组功能** - 恢复MessageGroupDetailActivity,支持创建和管理消息组
- ✅ **双企微弹窗处理** - WeworkAutoService统一处理双企微弹窗,使用坐标点击
- ✅ **第二次执行脚本失败修复** - 通过时间戳判断新任务启动,自动重置弹窗点击标志
- ✅ **智能识别消息页面** - 自动检测当前页面,自动导航到消息页面,支持从任何页面启动

**技术要点:**
- **双企微弹窗处理架构**: 只有WeworkAutoService处理弹窗,BatchSendService不处理
- **坐标点击 vs Resource-ID**: 使用坐标点击更可靠,不依赖resource-id
- **PackageNames限制**: BatchSendService只监听企微事件,不监听弹窗事件
- **时间戳判断新任务**: 使用`start_time`判断是否是新任务启动(3秒内),自动重置标志
- **isSelected属性检测**: 使用底部导航栏按钮的`isSelected`属性判断当前Tab
- **智能返回重试**: 找不到"消息"按钮时自动按返回键返回主页面,最多重试5次

### V3.0 (2025-12-18) - 批量发送功能完成
- ✅ **批量发送消息功能** - 从素材库聊天转发消息到多个群聊/联系人
- ✅ **智能滚动查找联系人** - 自动向下滚动直到找到目标联系人或到底部
- ✅ **全角/半角字符智能匹配** - 自动处理波浪号、空格、数字、字母的全角/半角差异
- ✅ **发送按钮双重点击策略** - performAction优先,坐标点击备用,确保点击成功
- ✅ **多选模式智能滚动** - 使用ACTION_SCROLL_BACKWARD在多选模式下向上滚动
- ✅ **完善的问题文档** - 详细记录所有问题的原因和解决方案

### V2.1 (2025-12-17)
- ✅ **修复"我的客户"点击问题** - 改为点击头像节点而非文本节点
- ✅ **智能页面状态检测** - 点击前先检查是否已在目标页面
- ✅ **自动重试机制** - 页面未切换时自动重试
- ✅ **移除回调依赖** - 不依赖GestureDescription回调,避免卡住
- ✅ **完善文档** - 详细记录正确的实现方法和关键技术点

### V2.0 (2025-12-15)
- ✅ **批量处理功能** - 一次性处理所有好友申请
- ✅ **批量邀请功能** - 一次性邀请所有新客户到群聊
- ✅ **智能页面检测** - 基于UI元素检测,更准确可靠
- ✅ **精准客户定位** - 只在"今天"分组下查找客户
- ✅ **完整的错误处理** - 自动重试和错误恢复
- ✅ **详细的操作日志** - 每一步都有清晰的日志记录

### V1.0 (2025-12-14)
- ✅ 实现自动通过好友申请
- ✅ 实现自动邀请进群
- ✅ 实现客户信息记录
- ✅ 实现智能判断邀请按钮
- ✅ 完整的错误处理机制

## 📄 许可证

MIT License
