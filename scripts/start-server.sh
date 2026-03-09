#!/bin/bash

set -e

echo "--------------- 서버 배포 시작 -----------------"

cd /home/ubuntu/onfilm-server/current

set -a

source /etc/onfilm.env

set +a

sudo fuser -k -n tcp 8080 || true

nohup java -jar project.jar --spring.profiles.active=prod > ./output.log 2>&1 &

echo "--------------- 서버 배포 끝 -----------------"