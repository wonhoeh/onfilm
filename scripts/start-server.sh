#!/bin/bash
set -e

echo "start 1" >> /home/ubuntu/start-debug.log

cd /home/ubuntu/onfilm-server/current
echo "start 2" >> /home/ubuntu/start-debug.log

set -a
source /etc/onfilm.env
set +a
echo "start 3" >> /home/ubuntu/start-debug.log

echo "DB_URL=$DB_URL" >> /home/ubuntu/start-debug.log

sudo fuser -k -n tcp 8080 || true
echo "start 4" >> /home/ubuntu/start-debug.log

nohup java -jar project.jar --spring.profiles.active=prod > /home/ubuntu/onfilm-server/current/output.log 2>&1 &
echo "start 5" >> /home/ubuntu/start-debug.log