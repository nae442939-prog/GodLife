@echo off
rem 앱 화면을 내 PC 의 크롬에 띄워 놓고 보면서 고친다 (데이터는 배포 서버 것을 쓴다).
rem 크롬이 뜨면 F12 → Ctrl+Shift+M 으로 폰 모양(iPhone)을 고른다.
rem 코드를 고친 뒤 이 창에서 r 을 누르면 바로 반영되고, q 를 누르면 끝난다.
chcp 65001 > nul
cd /d "%~dp0"
set FLUTTER=flutter
if exist "C:\dev\flutter\bin\flutter.bat" set FLUTTER=C:\dev\flutter\bin\flutter.bat
call %FLUTTER% run -d chrome
pause
