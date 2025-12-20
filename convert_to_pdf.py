#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将Markdown文档转换为HTML,然后可以用浏览器打印为PDF
"""

import re
import os

def markdown_to_html(md_file, html_file):
    """将Markdown转换为HTML"""
    
    # 读取Markdown文件
    with open(md_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 简单的Markdown转HTML规则
    html_content = content
    
    # 标题转换
    html_content = re.sub(r'^# (.+)$', r'<h1>\1</h1>', html_content, flags=re.MULTILINE)
    html_content = re.sub(r'^## (.+)$', r'<h2>\1</h2>', html_content, flags=re.MULTILINE)
    html_content = re.sub(r'^### (.+)$', r'<h3>\1</h3>', html_content, flags=re.MULTILINE)
    html_content = re.sub(r'^#### (.+)$', r'<h4>\1</h4>', html_content, flags=re.MULTILINE)
    
    # 粗体
    html_content = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', html_content)
    
    # 代码块
    html_content = re.sub(r'```(.+?)```', r'<pre><code>\1</code></pre>', html_content, flags=re.DOTALL)
    html_content = re.sub(r'`(.+?)`', r'<code>\1</code>', html_content)
    
    # 列表
    html_content = re.sub(r'^- (.+)$', r'<li>\1</li>', html_content, flags=re.MULTILINE)
    html_content = re.sub(r'^✅ (.+)$', r'<li>✅ \1</li>', html_content, flags=re.MULTILINE)
    html_content = re.sub(r'^⚠️ (.+)$', r'<li>⚠️ \1</li>', html_content, flags=re.MULTILINE)
    html_content = re.sub(r'^❌ (.+)$', r'<li>❌ \1</li>', html_content, flags=re.MULTILINE)
    
    # 包裹连续的li标签
    html_content = re.sub(r'(<li>.*?</li>\n)+', r'<ul>\g<0></ul>', html_content, flags=re.DOTALL)
    
    # 段落
    html_content = re.sub(r'\n\n', r'</p><p>', html_content)
    html_content = '<p>' + html_content + '</p>'
    
    # 水平线
    html_content = re.sub(r'<p>---</p>', r'<hr>', html_content)
    
    # 表格处理(简化版)
    html_content = re.sub(r'\|(.+?)\|', lambda m: '<td>' + m.group(1).strip() + '</td>', html_content)
    
    # HTML模板
    html_template = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>企业微信自动化应用 - 使用教程</title>
    <style>
        @page {{
            size: A4;
            margin: 2cm;
        }}
        
        body {{
            font-family: "Microsoft YaHei", "微软雅黑", Arial, sans-serif;
            line-height: 1.8;
            color: #333;
            max-width: 900px;
            margin: 0 auto;
            padding: 20px;
            background: #fff;
        }}
        
        h1 {{
            color: #2c3e50;
            border-bottom: 3px solid #3498db;
            padding-bottom: 10px;
            margin-top: 30px;
            page-break-after: avoid;
        }}
        
        h2 {{
            color: #34495e;
            border-left: 4px solid #3498db;
            padding-left: 15px;
            margin-top: 25px;
            page-break-after: avoid;
        }}
        
        h3 {{
            color: #555;
            margin-top: 20px;
            page-break-after: avoid;
        }}
        
        h4 {{
            color: #666;
            margin-top: 15px;
        }}
        
        p {{
            margin: 10px 0;
            text-align: justify;
        }}
        
        ul, ol {{
            margin: 10px 0;
            padding-left: 30px;
        }}
        
        li {{
            margin: 5px 0;
        }}
        
        code {{
            background: #f4f4f4;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: "Consolas", "Monaco", monospace;
            font-size: 0.9em;
        }}
        
        pre {{
            background: #f8f8f8;
            border: 1px solid #ddd;
            border-radius: 5px;
            padding: 15px;
            overflow-x: auto;
            page-break-inside: avoid;
        }}
        
        pre code {{
            background: none;
            padding: 0;
        }}
        
        table {{
            width: 100%;
            border-collapse: collapse;
            margin: 15px 0;
            page-break-inside: avoid;
        }}
        
        th, td {{
            border: 1px solid #ddd;
            padding: 10px;
            text-align: left;
        }}
        
        th {{
            background: #3498db;
            color: white;
            font-weight: bold;
        }}
        
        tr:nth-child(even) {{
            background: #f9f9f9;
        }}
        
        hr {{
            border: none;
            border-top: 2px solid #eee;
            margin: 30px 0;
        }}
        
        strong {{
            color: #2c3e50;
            font-weight: bold;
        }}
        
        .page-break {{
            page-break-after: always;
        }}
        
        @media print {{
            body {{
                padding: 0;
            }}
            
            h1, h2, h3, h4 {{
                page-break-after: avoid;
            }}
            
            pre, table {{
                page-break-inside: avoid;
            }}
        }}
    </style>
</head>
<body>
{html_content}
</body>
</html>"""
    
    # 写入HTML文件
    with open(html_file, 'w', encoding='utf-8') as f:
        f.write(html_template)
    
    print(f"✅ HTML文件已生成: {html_file}")
    print(f"\n📄 请用浏览器打开HTML文件,然后:")
    print(f"   1. 按 Cmd+P (Mac) 或 Ctrl+P (Windows)")
    print(f"   2. 选择'另存为PDF'")
    print(f"   3. 保存PDF文件")

if __name__ == "__main__":
    md_file = "企微自动化使用教程.md"
    html_file = "企微自动化使用教程.html"
    
    if not os.path.exists(md_file):
        print(f"❌ 找不到文件: {md_file}")
        exit(1)
    
    markdown_to_html(md_file, html_file)

