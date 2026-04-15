@echo off
chcp 65001 >nul
echo ========================================
echo PSU数据库初始化工具
echo ========================================
echo.

REM 尝试使用常见的Python路径
where python >nul 2>&1
if %errorlevel% equ 0 (
    python "%~dp0init_database.py"
    goto :end
)

where python3 >nul 2>&1
if %errorlevel% equ 0 (
    python3 "%~dp0init_database.py"
    goto :end
)

where py >nul 2>&1
if %errorlevel% equ 0 (
    py "%~dp0init_database.py"
    goto :end
)

REM 尝试常见的Python安装路径
if exist "C:\Python39\python.exe" (
    "C:\Python39\python.exe" "%~dp0init_database.py"
    goto :end
)

if exist "C:\Python310\python.exe" (
    "C:\Python310\python.exe" "%~dp0init_database.py"
    goto :end
)

if exist "C:\Python311\python.exe" (
    "C:\Python311\python.exe" "%~dp0init_database.py"
    goto :end
)

if exist "C:\Python312\python.exe" (
    "C:\Python312\python.exe" "%~dp0init_database.py"
    goto :end
)

if exist "%LOCALAPPDATA%\Programs\Python\Python39\python.exe" (
    "%LOCALAPPDATA%\Programs\Python\Python39\python.exe" "%~dp0init_database.py"
    goto :end
)

if exist "%LOCALAPPDATA%\Programs\Python\Python310\python.exe" (
    "%LOCALAPPDATA%\Programs\Python\Python310\python.exe" "%~dp0init_database.py"
    goto :end
)

if exist "%LOCALAPPDATA%\Programs\Python\Python311\python.exe" (
    "%LOCALAPPDATA%\Programs\Python\Python311\python.exe" "%~dp0init_database.py"
    goto :end
)

if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" (
    "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" "%~dp0init_database.py"
    goto :end
)

echo 错误: 未找到Python解释器
echo 请确保已安装Python并将其添加到系统PATH中
echo.
echo 或者手动执行: python "%~dp0init_database.py"
echo.
pause
exit /b 1

:end
echo.
pause
