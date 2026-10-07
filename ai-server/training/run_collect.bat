@echo off
chcp 65001 > nul
set PYTHONIOENCODING=utf-8
cd /d "%~dp0"

set PY=python
if exist "..\venv\Scripts\python.exe" set PY=..\venv\Scripts\python.exe

echo [1/2] 필요한 패키지 설치 중...
%PY% -m pip install pillow imagehash fiftyone
if errorlevel 1 (
  echo 패키지 설치 실패. 이 창의 오류 내용을 확인해 주세요.
  pause
  exit /b 1
)

echo.
echo [2/2] Open Images에서 사진 받는 중... (시간이 꽤 걸려요)
%PY% collect_images.py openimages --per-label 150
echo.
echo 끝! 결과는 data\openimages\report.txt 에도 저장됐어요.
pause
