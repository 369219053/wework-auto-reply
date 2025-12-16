package com.wework.autoreply

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务辅助类 - UI元素查找和操作
 */
object AccessibilityHelper {

    private const val TAG = "AccessibilityHelper"
    private var service: AccessibilityService? = null

    /**
     * 设置AccessibilityService实例
     */
    fun setService(accessibilityService: AccessibilityService) {
        service = accessibilityService
    }
    
    /**
     * 根据文本查找节点
     */
    fun findNodeByText(rootNode: AccessibilityNodeInfo?, text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        if (rootNode == null) return null
        
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByText(rootNode, text, exact, nodes)
        return nodes.firstOrNull()
    }
    
    /**
     * 根据文本查找所有匹配的节点
     */
    fun findNodesByText(rootNode: AccessibilityNodeInfo?, text: String, exact: Boolean = false): List<AccessibilityNodeInfo> {
        if (rootNode == null) return emptyList()
        
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByText(rootNode, text, exact, nodes)
        return nodes
    }
    
    private fun findNodesByText(node: AccessibilityNodeInfo, text: String, exact: Boolean, result: MutableList<AccessibilityNodeInfo>) {
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        
        val matches = if (exact) {
            nodeText == text || contentDesc == text
        } else {
            nodeText.contains(text) || contentDesc.contains(text)
        }
        
        if (matches) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findNodesByText(child, text, exact, result)
            }
        }
    }
    
    /**
     * 根据resource-id查找节点
     */
    fun findNodeByResourceId(rootNode: AccessibilityNodeInfo?, resourceId: String): AccessibilityNodeInfo? {
        if (rootNode == null) return null
        
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByResourceId(rootNode, resourceId, nodes)
        return nodes.firstOrNull()
    }
    
    /**
     * 根据resource-id查找所有匹配的节点
     */
    fun findNodesByResourceId(rootNode: AccessibilityNodeInfo?, resourceId: String): List<AccessibilityNodeInfo> {
        if (rootNode == null) return emptyList()
        
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByResourceId(rootNode, resourceId, nodes)
        return nodes
    }
    
    private fun findNodesByResourceId(node: AccessibilityNodeInfo, resourceId: String, result: MutableList<AccessibilityNodeInfo>) {
        if (node.viewIdResourceName == resourceId) {
            result.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findNodesByResourceId(child, resourceId, result)
            }
        }
    }
    
    /**
     * 根据Y坐标范围过滤节点
     */
    fun filterNodesByYRange(nodes: List<AccessibilityNodeInfo>, minY: Int, maxY: Int): List<AccessibilityNodeInfo> {
        return nodes.filter { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.top >= minY && rect.top <= maxY
        }
    }
    
    /**
     * 点击节点
     */
    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // 如果节点本身不可点击,尝试点击父节点
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                parent = parent.parent
            }
            false
        }
    }
    
    /**
     * 获取节点的屏幕坐标
     */
    fun getNodeBounds(node: AccessibilityNodeInfo?): Rect? {
        if (node == null) return null
        
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect
    }
    
    /**
     * 获取节点中心坐标
     */
    fun getNodeCenter(node: AccessibilityNodeInfo?): Pair<Int, Int>? {
        val rect = getNodeBounds(node) ?: return null
        return Pair(rect.centerX(), rect.centerY())
    }
    
    /**
     * 延迟执行
     */
    fun sleep(millis: Long) {
        Thread.sleep(millis)
    }
    
    /**
     * 在主线程延迟执行
     */
    fun postDelayed(delayMillis: Long, action: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed(action, delayMillis)
    }

    /**
     * 通过坐标点击 (使用手势模拟)
     */
    fun tapByCoordinate(x: Int, y: Int, callback: ((Boolean) -> Unit)? = null) {
        if (service == null) {
            Log.e(TAG, "AccessibilityService未设置")
            callback?.invoke(false)
            return
        }

        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        service?.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "点击坐标成功: [$x, $y]")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "点击坐标失败: [$x, $y]")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * 通过坐标点击 (同步版本,带延迟)
     */
    fun tap(x: Int, y: Int, delayMillis: Long = 1000) {
        var completed = false

        tapByCoordinate(x, y) { success ->
            completed = true
        }

        // 等待手势完成
        var waited = 0L
        while (!completed && waited < 3000) {
            Thread.sleep(100)
            waited += 100
        }

        // 额外延迟
        if (delayMillis > 0) {
            Thread.sleep(delayMillis)
        }
    }

    /**
     * 模拟返回键
     */
    fun back(delayMillis: Long = 1000) {
        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (delayMillis > 0) {
            Thread.sleep(delayMillis)
        }
    }
}

