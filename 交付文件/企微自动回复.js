/**
 * 企业微信自动回复脚本 (Hamibot版本)
 * 
 * 功能:
 * 1. 监听企业微信的新好友通知
 * 2. 自动点击通知打开聊天窗口
 * 3. 自动发送欢迎语和群聊邀请链接
 * 
 * 使用方法:
 * 1. 安装Hamibot应用
 * 2. 导入本脚本
 * 3. 修改下方的配置项
 * 4. 运行脚本
 * 
 * 作者: 小牛马
 * 版本: 1.0
 * 日期: 2025-12-14
 */

// ==================== 配置区域 ====================

// 欢迎语配置
const CONFIG = {
    // 欢迎语文本(请修改为您的实际内容)
    welcomeMessage: "您好!欢迎添加,请点击链接加入我们的群聊: [您的群聊链接]",
    
    // 通知关键词(用于识别新好友通知)
    notificationKeywords: ["新的朋友", "添加了你", "通过了你的好友申请"],
    
    // 延迟时间配置(单位:毫秒)
    delays: {
        afterNotification: 2000,  // 收到通知后等待时间
        afterClick: 1500,         // 点击通知后等待时间
        afterInput: 500,          // 输入文本后等待时间
        beforeReturn: 1000        // 返回主屏幕前等待时间
    },
    
    // 工作时间限制(可选,留空则全天运行)
    workingHours: {
        enabled: false,           // 是否启用工作时间限制
        start: 9,                 // 开始时间(小时,24小时制)
        end: 18                   // 结束时间(小时,24小时制)
    },
    
    // 日志配置
    logging: {
        enabled: true,            // 是否启用日志
        showToast: true           // 是否显示Toast提示
    }
};

// ==================== 主程序 ====================

// 请求无障碍权限
auto.waitFor();

// 日志函数
function log(message) {
    if (CONFIG.logging.enabled) {
        console.log("[企微自动回复] " + message);
    }
    if (CONFIG.logging.showToast) {
        toast(message);
    }
}

// 检查是否在工作时间
function isWorkingHours() {
    if (!CONFIG.workingHours.enabled) {
        return true;
    }
    
    const now = new Date();
    const hour = now.getHours();
    return hour >= CONFIG.workingHours.start && hour < CONFIG.workingHours.end;
}

// 检查通知是否匹配
function isTargetNotification(title, text) {
    const content = (title || "") + " " + (text || "");
    return CONFIG.notificationKeywords.some(keyword => content.includes(keyword));
}

// 发送欢迎消息
function sendWelcomeMessage() {
    try {
        log("开始发送欢迎消息...");
        
        // 等待聊天窗口打开
        sleep(CONFIG.delays.afterClick);
        
        // 查找输入框
        const inputBox = className("EditText").findOne(5000);
        if (!inputBox) {
            log("错误: 未找到输入框");
            return false;
        }
        
        // 输入欢迎语
        inputBox.setText(CONFIG.welcomeMessage);
        log("已输入欢迎语");
        
        // 等待输入完成
        sleep(CONFIG.delays.afterInput);
        
        // 查找发送按钮
        const sendBtn = text("发送").findOne(3000);
        if (!sendBtn) {
            log("错误: 未找到发送按钮");
            return false;
        }
        
        // 点击发送
        sendBtn.click();
        log("已发送欢迎消息");
        
        // 等待发送完成
        sleep(CONFIG.delays.beforeReturn);
        
        // 返回主屏幕
        home();
        log("已返回主屏幕");
        
        return true;
    } catch (error) {
        log("错误: " + error.message);
        return false;
    }
}

// 主函数
function main() {
    log("企微自动回复脚本已启动");
    log("配置: " + JSON.stringify(CONFIG));
    
    // 监听通知
    events.observeNotification();
    events.on("notification", function(notification) {
        try {
            // 获取通知信息
            const packageName = notification.getPackageName();
            const title = notification.getTitle();
            const text = notification.getText();
            
            // 检查是否是企业微信通知
            if (packageName !== "com.tencent.wework") {
                return;
            }
            
            // 检查是否是目标通知
            if (!isTargetNotification(title, text)) {
                return;
            }
            
            log("收到新好友通知: " + title);
            
            // 检查工作时间
            if (!isWorkingHours()) {
                log("当前不在工作时间,跳过处理");
                return;
            }
            
            // 等待通知完全显示
            sleep(CONFIG.delays.afterNotification);
            
            // 点击通知
            notification.click();
            log("已点击通知");
            
            // 发送欢迎消息
            sendWelcomeMessage();
            
        } catch (error) {
            log("处理通知时出错: " + error.message);
        }
    });
    
    // 保持脚本运行
    log("正在监听企业微信通知...");
    setInterval(function() {
        // 每分钟输出一次心跳日志
    }, 60000);
}

// 启动主程序
main();

