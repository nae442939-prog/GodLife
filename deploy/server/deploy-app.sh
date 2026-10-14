#!/bin/bash
# Flutter 웹 빌드를 서버의 /app/ 아래로 올린다
set -e
APPDIR=/opt/godlife/app
sudo mkdir -p $APPDIR && sudo chown ubuntu:ubuntu $APPDIR
find $APPDIR -mindepth 1 -delete
tar -xzf ~/flutter-web.tgz -C $APPDIR --strip-components=1
CONF=/etc/nginx/sites-available/godlife
if ! grep -q 'location /app/' $CONF; then
  sudo sed -i '0,/    location \/ {/s//    # Flutter 앱 (웹 빌드)\n    location \/app\/ {\n        alias \/opt\/godlife\/app\/;\n        try_files $uri $uri\/ \/app\/index.html;\n    }\n\n    location \/ {/' $CONF
fi
sudo nginx -t && sudo systemctl reload nginx
curl -s -o /dev/null -w 'app=%{http_code} ' https://mygodlife.duckdns.org/app/
curl -s -o /dev/null -w 'main.dart.js=%{http_code} %{size_download}B\n' https://mygodlife.duckdns.org/app/main.dart.js
grep -n -A4 'location /app/' $CONF | head -8
