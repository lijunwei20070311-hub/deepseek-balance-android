@echo off
rem ============================================================
rem  联调自检：用真实 API Key 直接访问 DeepSeek 接口，
rem  确认手机以外的网络/Key/接口格式都没问题。
rem
rem  用法：  check-api.bat sk-你的key
rem  说明：  1) 查询余额  2) 发一次最小对话并打印 usage
rem ============================================================
setlocal
set KEY=%~1
if "%KEY%"=="" (
  echo 用法: check-api.bat sk-xxxxxxxx
  exit /b 1
)

set CURL=%SystemRoot%\System32\curl.exe
set BASE=https://api.deepseek.com

echo [1/2] 查询余额 GET %BASE%/user/balance
"%CURL%" -sS --ssl-no-revoke -m 30 -H "Authorization: Bearer %KEY%" "%BASE%/user/balance"
echo.
echo.

echo [2/2] 最小对话调用 POST %BASE%/chat/completions
"%CURL%" -sS --ssl-no-revoke -m 60 -X POST "%BASE%/chat/completions" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %KEY%" ^
  -d "{\"model\":\"deepseek-v4-flash\",\"messages\":[{\"role\":\"user\",\"content\":\"只回复两个字：正常\"}],\"max_tokens\":16,\"stream\":false}"
echo.
endlocal
