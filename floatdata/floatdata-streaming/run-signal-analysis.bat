@echo off
chcp 65001 > nul
echo ============================================================
echo 🎯 信号滤波分析系统
echo ============================================================
echo.

echo 📦 检查依赖项...
python -c "import numpy, matplotlib, scipy, nptdms" 2>nul
if %errorlevel% neq 0 (
    echo ❌ 依赖项未安装！
    echo.
    echo 正在安装依赖项...
    pip install -r requirements-signal-analysis.txt
    echo.
)

:menu
echo.
echo ============================================================
echo 请选择要运行的分析：
echo ============================================================
echo.
echo [1] Signal-1 分析 (单文件多通道数据)
echo [2] Signal-2 分析 (多文件分离数据)
echo [3] 运行所有分析
echo [4] 查看使用说明
echo [5] 退出
echo.
set /p choice="请输入选项 (1-5): "

if "%choice%"=="1" goto signal1
if "%choice%"=="2" goto signal2
if "%choice%"=="3" goto all
if "%choice%"=="4" goto readme
if "%choice%"=="5" goto end
echo ❌ 无效选项！
goto menu

:signal1
echo.
echo ============================================================
echo 🔄 正在运行 Signal-1 分析...
echo ============================================================
python signal-filter-visualizer.py
if %errorlevel% equ 0 (
    echo.
    echo ✅ Signal-1 分析完成！
    echo 📁 结果保存在: floatdata\signal-1\
    start "" "..\signal-1"
) else (
    echo ❌ 分析失败！
)
pause
goto menu

:signal2
echo.
echo ============================================================
echo 🔄 正在运行 Signal-2 分析...
echo ============================================================
python signal-filter-visualizer-v2.py
if %errorlevel% equ 0 (
    echo.
    echo ✅ Signal-2 分析完成！
    echo 📁 结果保存在: floatdata\signal-2\
    start "" "..\signal-2"
) else (
    echo ❌ 分析失败！
)
pause
goto menu

:all
echo.
echo ============================================================
echo 🔄 正在运行所有分析...
echo ============================================================
echo.
echo [1/2] Signal-1 分析...
python signal-filter-visualizer.py
echo.
echo [2/2] Signal-2 分析...
python signal-filter-visualizer-v2.py
echo.
echo ✅ 所有分析完成！
echo 📁 Signal-1 结果: floatdata\signal-1\
echo 📁 Signal-2 结果: floatdata\signal-2\
pause
goto menu

:readme
echo.
start "" "信号滤波分析使用指南.md"
goto menu

:end
echo.
echo 👋 感谢使用！
exit /b 0
