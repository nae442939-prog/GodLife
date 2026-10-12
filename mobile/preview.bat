@echo off
rem 앱 화면을 크롬에 폰 모양으로 띄워서 본다.
rem 크롬이 뜨면 F12 → Ctrl+Shift+M 으로 폰 모양(iPhone)을 고른다.
rem
rem 배포 서버에 올라가 있는 앱을 연다 (이 PC 는 메모리가 8GB 라 개발 모드로 띄우면 멈춘다).
rem 메모리가 넉넉한 PC 에서는 아래 줄 대신 'flutter run -d chrome' 을 쓰면 고친 내용이 r 키로 바로 반영된다.
start "" chrome "https://52-79-61-201.sslip.io/app/"
