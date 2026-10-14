#!/bin/bash
# 웹사이트(React 빌드)를 서버에 올린다: 새 폴더에 풀고 이름을 바꿔 끼운다
set -e
F=/opt/godlife/frontend
STAGE=$(mktemp -d -p $F)
tar -xzf ~/frontend-dist.tgz -C $STAGE
OLD=$F/dist.$(date +%H%M%S)
mv $F/dist $OLD
mv $STAGE/dist $F/dist
find $STAGE -maxdepth 0 -empty -delete
find $F -maxdepth 1 -name 'dist.*' -not -path "$OLD" -exec rm -r {} +
curl -s -o /dev/null -w 'web=%{http_code}\n' -A 'Mozilla/5.0 (Windows NT 10.0) Chrome/154' https://mygodlife.duckdns.org/login
