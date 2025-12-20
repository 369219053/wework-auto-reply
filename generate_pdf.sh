#!/bin/bash
# 使用Chrome/Safari打印HTML为PDF

HTML_FILE="企微自动化使用教程.html"
PDF_FILE="企微自动化使用教程.pdf"

# 获取HTML文件的绝对路径
HTML_PATH="$(pwd)/$HTML_FILE"

echo "📄 正在生成PDF..."
echo "HTML文件: $HTML_PATH"
echo "PDF文件: $PDF_FILE"

# 方法1: 使用Chrome headless模式
if command -v /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome &> /dev/null; then
    echo "✅ 使用Chrome生成PDF..."
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
        --headless \
        --disable-gpu \
        --print-to-pdf="$PDF_FILE" \
        --no-margins \
        "file://$HTML_PATH"
    
    if [ -f "$PDF_FILE" ]; then
        echo "✅ PDF生成成功: $PDF_FILE"
        open "$PDF_FILE"
        exit 0
    fi
fi

# 方法2: 使用wkhtmltopdf
if command -v wkhtmltopdf &> /dev/null; then
    echo "✅ 使用wkhtmltopdf生成PDF..."
    wkhtmltopdf "$HTML_FILE" "$PDF_FILE"
    
    if [ -f "$PDF_FILE" ]; then
        echo "✅ PDF生成成功: $PDF_FILE"
        open "$PDF_FILE"
        exit 0
    fi
fi

# 方法3: 使用cupsfilter (macOS自带)
if command -v cupsfilter &> /dev/null; then
    echo "✅ 使用cupsfilter生成PDF..."
    cupsfilter "$HTML_FILE" > "$PDF_FILE" 2>/dev/null
    
    if [ -f "$PDF_FILE" ] && [ -s "$PDF_FILE" ]; then
        echo "✅ PDF生成成功: $PDF_FILE"
        open "$PDF_FILE"
        exit 0
    fi
fi

# 如果所有方法都失败,提示手动操作
echo "⚠️ 自动生成PDF失败,请手动操作:"
echo "1. 浏览器已打开HTML文件"
echo "2. 按 Cmd+P 打开打印对话框"
echo "3. 选择'另存为PDF'"
echo "4. 保存为: $PDF_FILE"

open "$HTML_FILE"

