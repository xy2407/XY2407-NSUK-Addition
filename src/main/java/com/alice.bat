@echo off
setlocal enabledelayedexpansion

set "total=0"
echo 正在统计当前目录及子目录下所有 .java 文件的行数...
echo.

for /r %%f in (*.java) do (
    for /f %%c in ('type "%%f" ^| find /c /v ""') do (
        set "lines=%%c"
        echo !lines!	%%f
        set /a total+=!lines!
    )
)

echo.
echo ================================
echo 总行数: !total!
echo ================================

endlocal
pause