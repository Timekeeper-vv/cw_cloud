@echo off
chcp 65001 > nul
echo ============================================================
echo 🔬 TDMS信号滤波分析系统
echo ============================================================
echo.

:menu
echo.
echo 请选择要分析的信号：
echo.
echo [1] Signal-1 数据 (ae_sim_2s.tdms - 单文件多通道)
echo [2] Signal-2 数据 (多文件分离)
echo [3] 自定义文件
echo [4] 退出
echo.
set /p choice="请输入选项 (1-4): "

if "%choice%"=="1" goto signal1
if "%choice%"=="2" goto signal2
if "%choice%"=="3" goto custom
if "%choice%"=="4" goto end
echo ❌ 无效选项！
goto menu

:signal1
echo.
echo ============================================================
echo 📊 分析 Signal-1 数据
echo ============================================================
echo 文件: signal-1\ae_sim_2s.tdms
echo 采样率: 100 kHz
echo 包含通道: time_s, sine, noise, sine_plus_noise
echo ============================================================
echo.
python tdms-reader.py --visualize -s 100000
if %errorlevel% equ 0 (
    echo.
    echo ✅ 分析完成！图片已保存到 signal-1 文件夹
    start "" "..\signal-1"
) else (
    echo.
    echo ❌ 分析失败！请检查依赖项是否安装：
    echo    pip install numpy matplotlib scipy nptdms
)
pause
goto menu

:signal2
echo.
echo ============================================================
echo 📊 分析 Signal-2 数据
echo ============================================================
echo 文件夹: signal-2\
echo 包含: ae_sine_2s.tdms, ae_noise_2s.tdms, ae_mix_2s.tdms
echo 采样率: 100 kHz
echo ============================================================
echo.
python tdms-reader.py --visualize --file "..\signal-2\ae_mix_2s.tdms" -s 100000
if %errorlevel% equ 0 (
    echo.
    echo ✅ 分析完成！图片已保存到 signal-2 文件夹
    start "" "..\signal-2"
) else (
    echo.
    echo ❌ 分析失败！请检查依赖项是否安装：
    echo    pip install numpy matplotlib scipy nptdms
)
pause
goto menu

:custom
echo.
set /p filepath="请输入TDMS文件完整路径: "
set /p samplerate="请输入采样率(Hz，默认100000): "

if "%samplerate%"=="" set samplerate=100000

echo.
echo ============================================================
echo 📊 分析自定义文件
echo ============================================================
echo 文件: %filepath%
echo 采样率: %samplerate% Hz
echo ============================================================
echo.
python tdms-reader.py --visualize --file "%filepath%" -s %samplerate%
if %errorlevel% equ 0 (
    echo.
    echo ✅ 分析完成！
) else (
    echo.
    echo ❌ 分析失败！
)
pause
goto menu

:end
echo.
echo 👋 感谢使用！
exit /b 0
