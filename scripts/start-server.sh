#!/bin/bash

set -e

echo "--------------- 서버 배포 시작 -----------------"

cd /home/ubuntu/onfilm-server/current

sudo fuser -k -n tcp 8080 || true

if ! command -v ffprobe >/dev/null 2>&1; then
sudo apt-get update
sudo apt-get install -y ffmpeg
fi

nohup java -jar project.jar > ./output.log 2>&1 &

echo "--------------- 서버 배포 끝 -----------------"