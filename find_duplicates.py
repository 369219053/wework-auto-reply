#!/usr/bin/env python3
import re

file_path = "wework-auto-reply/app/src/main/java/com/wework/autoreply/WeworkAutoService.kt"

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# 查找所有 findScrollableNode 方法定义
pattern = r'^\s*private fun findScrollableNode'
matches = []

for i, line in enumerate(lines, 1):
    if re.match(pattern, line):
        matches.append((i, line.strip()))

print(f"找到 {len(matches)} 个 findScrollableNode 方法定义:")
for line_num, line_content in matches:
    print(f"  行 {line_num}: {line_content}")

# 查找所有 monitorPage 引用
pattern2 = r'monitorPage[^C]'
matches2 = []

for i, line in enumerate(lines, 1):
    if re.search(pattern2, line):
        matches2.append((i, line.strip()))

print(f"\n找到 {len(matches2)} 个 monitorPage 引用:")
for line_num, line_content in matches2:
    print(f"  行 {line_num}: {line_content}")

print(f"\n文件总行数: {len(lines)}")

