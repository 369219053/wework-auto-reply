# 企业微信UI结构文档

> 本文档记录企业微信各个页面的UI元素结构,用于自动化脚本开发和调试

---

## 📋 目录

1. [消息页面](#消息页面)
2. [通讯录页面](#通讯录页面)
3. [新的客户列表页面](#新的客户列表页面)
4. [客户详情页面](#客户详情页面)
5. [群聊页面](#群聊页面)
6. [群详情页面](#群详情页面)
7. [全部群成员页面](#全部群成员页面)
8. [添加成员选择页面](#添加成员选择页面)
9. [我的客户列表页面](#我的客户列表页面)

---

## 消息页面

**Activity**: `com.tencent.wework/.launch.WwMainActivity`

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 底部导航-消息 | `com.tencent.wework:id/xxx` | "消息" | 底部导航栏 |
| 底部导航-通讯录 | `com.tencent.wework:id/xxx` | "通讯录" | 底部导航栏 |
| 群聊列表项 | - | 群聊名称 | 可点击进入群聊 |

### UI Dump示例
```xml
待补充...
```

---

## 通讯录页面

**Activity**: `com.tencent.wework/.launch.WwMainActivity`

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 添加客户 | - | "添加客户" | 点击进入添加客户页面 |

### UI Dump示例
```xml
待补充...
```

---

## 新的客户列表页面

**Activity**: `com.tencent.wework/.launch.WwMainActivity`

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 查看按钮 | - | "查看" | 每个好友申请右侧的按钮 |
| 客户名称 | - | 客户名称 | 好友申请的客户名称 |

### UI Dump示例
```xml
待补充...
```

### 注意事项
- 可能有多个"查看"按钮,每个对应一个好友申请
- 需要循环处理所有"查看"按钮

---

## 客户详情页面

**Activity**: `com.tencent.wework/.launch.WwMainActivity`

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 客户名称 | `com.tencent.wework:id/moj` | 客户名称 | 页面顶部的客户名称 |
| 通过验证按钮 | - | "通过验证" | 点击通过好友申请 |
| 完成按钮 | - | "完成" | 通过验证后出现 |

### UI Dump示例
```xml
待补充...
```

---

## 群聊页面

**Activity**: `com.tencent.wework/.launch.WwMainActivity`

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 右上角三个点 | - | - | 点击进入群详情 |

### UI Dump示例
```xml
待补充...
```

---

## 群详情页面

**Activity**: `com.tencent.wework/.launch.WwMainActivity`

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 查看全部群成员 | - | "查看全部群成员" | 点击查看所有成员 |

### UI Dump示例
```xml
待补充...
```

---

## 全部群成员页面

**Activity**: `com.tencent.wework/.launch.WwMainActivity`

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 添加按钮 | - | "添加" | 右上角的添加按钮 |

### UI Dump示例
```xml
待补充...
```

---

## 添加成员选择页面

**Activity**: `com.tencent.wework/.launch.WwMainActivity`

### 页面说明

点击"添加"按钮后进入此页面,显示三个分类标签:
- **我的客户** - 显示所有客户
- **企业通讯录** - 显示企业内部联系人
- **最近联系人** - 显示最近联系的人

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 页面标题 | `com.tencent.wework:id/nh3` | "添加群成员" | 页面顶部标题 |
| 我的客户-头像 | `com.tencent.wework:id/lmb` | - | 我的客户分类标签的头像,**可点击** |
| 我的客户-文本 | - | "我的客户" | 我的客户分类标签的文本,**不可点击** |
| 企业通讯录-头像 | `com.tencent.wework:id/lmb` | - | 企业通讯录分类标签的头像 |
| 企业通讯录-文本 | - | "企业通讯录" | 企业通讯录分类标签的文本 |
| 最近联系人标题 | `com.tencent.wework:id/n_s` | "最近联系人" | 最近联系人分组标题 |

### UI结构

```xml
<node resource-id="com.tencent.wework:id/cmd" class="android.widget.RelativeLayout">
  <node resource-id="com.tencent.wework:id/hbv" class="android.widget.RelativeLayout">
    <!-- 头像节点 (可点击) -->
    <node resource-id="com.tencent.wework:id/h8z" class="android.widget.FrameLayout">
      <node resource-id="com.tencent.wework:id/lmb"
            class="android.widget.ImageView"
            clickable="true"
            enabled="true"
            bounds="[32,187][88,253]" />
    </node>
    <!-- 文本节点 (不可点击) -->
    <node resource-id="com.tencent.wework:id/gan" class="android.widget.RelativeLayout">
      <node text="我的客户"
            class="android.widget.TextView"
            clickable="false"
            bounds="[112,198][240,241]" />
    </node>
  </node>
</node>
```

### ⭐ 点击"我的客户"的正确方法

**问题**: 直接点击"我的客户"文本节点或使用坐标点击都不稳定

**正确方法**: 点击"我的客户"分类标签的头像节点

**实现步骤**:
1. 使用`findNodeByTextExact()`查找"我的客户"文本节点
2. 向上遍历父节点,找到`resource-id="com.tencent.wework:id/cmd"`的父节点
3. 在该父节点下查找`resource-id="com.tencent.wework:id/lmb"`的头像节点
4. 使用`clickNode()`点击头像节点

**代码示例** (Kotlin):
```kotlin
// 1. 查找"我的客户"文本节点
val myCustomersTextNode = findNodeByTextExact(rootNode, "我的客户")

if (myCustomersTextNode != null) {
    // 2. 向上遍历找到cmd父节点
    var parent = myCustomersTextNode.parent
    var cmdNode: AccessibilityNodeInfo? = null
    var depth = 0

    while (parent != null && depth < 10) {
        if (parent.viewIdResourceName == "com.tencent.wework:id/cmd") {
            cmdNode = parent
            break
        }
        parent = parent.parent
        depth++
    }

    if (cmdNode != null) {
        // 3. 在cmd节点下查找头像节点
        val avatarNode = findNodeByResourceId(cmdNode, "com.tencent.wework:id/lmb")

        if (avatarNode != null) {
            // 4. 点击头像节点
            clickNode(avatarNode)
        }
    }
}
```

**关键原因**:
- ✅ 头像节点是明确可点击的UI元素(`clickable="true"`)
- ✅ 使用`clickNode()`方法有三层fallback机制
- ❌ 文本节点不可点击(`clickable="false"`)
- ❌ 坐标点击在某些设备上不稳定

### 注意事项
- 点击"我的客户"后,页面会切换到"我的客户列表页面"
- 切换后页面标题仍然是"添加群成员",但会出现"根据标签筛选"文本
- 需要通过查找"根据标签筛选"文本来判断是否已切换到"我的客户"页面

---

## 我的客户列表页面

**Activity**: `com.tencent.wework/.launch.WwMainActivity`

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 页面标题 | - | "添加群成员" | 页面顶部标题 |
| 确定按钮 | `com.tencent.wework:id/nhn` | "确定" 或 "" | 右上角确定按钮,text可能为空 |
| 今天分组 | - | "今天" | 客户分组标题 |
| 日期分组 | - | "12-15" 等 | 历史客户分组 |
| 客户名称 | - | 客户名称 | 可点击勾选 |
| 勾选框 | `com.tencent.wework:id/lmb` | - | ImageView,enabled属性表示是否可勾选 |

### UI Dump示例
```xml
待补充...
```

### 注意事项
- 客户列表分为多个分组:"今天"、日期分组(如"12-15")、"最近联系人"等
- 需要通过Y坐标过滤,只选择"今天"分组下的客户
- 勾选框的enabled属性:
  - `enabled="true"` - 可以勾选(客户不在群里)
  - `enabled="false"` - 不可勾选(客户已在群里)
- "确定"按钮的text属性可能为空,需要通过resource-id查找

---

---

## 邀请确认弹窗

**触发条件**: 当群聊人数较多时,点击"确定"按钮后会弹出确认对话框

### 关键元素

| 元素 | Resource-ID | Text | 说明 |
|------|-------------|------|------|
| 提示文字 | - | "当前群聊人数较多,为减少打扰,对方同意后才会进入群聊,现在邀请?" | 弹窗提示 |
| 取消按钮 | - | "取消" | 左侧按钮 |
| 邀请按钮 | - | "邀请" | 右侧按钮,需要点击 |

### 处理逻辑
- 点击"确定"按钮后等待1秒
- 检测是否有"邀请"按钮
- 如果有,点击"邀请"按钮
- 如果没有,说明是小群聊,直接完成

---

## 🔍 关键技术点

### 1. 客户列表勾选逻辑

**问题**: 客户列表分为多个分组,可能有重名客户

**解决方案**:
1. 查找"今天"分组的Y坐标范围
2. 查找下一个分组(如"12-15")的Y坐标
3. 只选择Y坐标在"今天"分组范围内的客户
4. 点击客户名称text元素的中心坐标

**代码示例**:
```javascript
// 查找"今天"分组
const todayY2 = parseInt(todayMatch[4]);

// 查找下一个分组
const nextGroupY1 = nextGroupMatch ? parseInt(nextGroupMatch[2]) : 9999;

// 过滤客户
const todayCustomers = customers.filter(c => c.y1 > todayY2 && c.y1 < nextGroupY1);
```

### 2. 客户名称去重

**问题**: 可能有多个不同的人使用相同的名字

**解决方案**:
```javascript
// 去重客户名称,避免重复勾选导致取消勾选
const uniqueCustomerNames = [...new Set(customerNames)];
```

### 3. 确定按钮查找

**问题**: "确定"按钮的text属性可能为空

**解决方案**:
```javascript
// 先尝试通过text查找
let confirmRegex = /text="确定[^"]*"[^>]*bounds=.../;
let confirmMatch = uiXml.match(confirmRegex);

// 如果找不到,通过resource-id查找
if (!confirmMatch) {
  confirmRegex = /resource-id="com\.tencent\.wework:id\/nhn"[^>]*bounds=.../;
  confirmMatch = uiXml.match(confirmRegex);
}
```

### 4. 已在群里的客户检测

**问题**: 客户已在群里时,勾选框是灰色的,无法勾选

**解决方案**:
```javascript
// 查找客户对应的ImageView,检查enabled状态
const imageViewPattern = new RegExp(
  `resource-id="com\\.tencent\\.wework:id/lmb"[^>]*bounds="\\[[0-9]+,${customer.y1 - 50}\\]\\[[0-9]+,${customer.y1 + 100}\\]"[^>]*enabled="(true|false)"`,
  'i'
);
const imageViewMatch = uiXml.match(imageViewPattern);

if (imageViewMatch && imageViewMatch[1] === 'false') {
  // 客户已在群里,跳过
  continue;
}
```

---

## 📝 更新日志

### 2025-12-17
- ✅ **补充"添加成员选择页面"详细结构** - 记录完整的UI层级和元素信息
- ✅ **添加点击"我的客户"的正确方法** - 详细说明为什么要点击头像而非文本
- ✅ **提供完整代码示例** - Kotlin代码示例,包含完整的实现步骤
- ✅ **记录关键原因** - 说明为什么文本节点和坐标点击不稳定

### 2025-12-16
- 创建UI结构文档
- 添加基础页面结构框架
- 补充邀请确认弹窗信息
- 添加关键技术点说明
- 记录成功测试的坐标信息

