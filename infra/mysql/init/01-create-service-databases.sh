#!/usr/bin/env bash

set -Eeuo pipefail

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${ONFILM_API_DB_PASSWORD:?ONFILM_API_DB_PASSWORD is required}"
: "${ONFILM_WORKER_DB_PASSWORD:?ONFILM_WORKER_DB_PASSWORD is required}"

escape_sql_string() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\'/\'\'}"
    printf '%s' "$value"
}

api_password="$(escape_sql_string "$ONFILM_API_DB_PASSWORD")"
worker_password="$(escape_sql_string "$ONFILM_WORKER_DB_PASSWORD")"

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`onfilm_api\`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS \`onfilm_worker\`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'onfilm_api_app'@'%' IDENTIFIED BY '${api_password}';
ALTER USER 'onfilm_api_app'@'%' IDENTIFIED BY '${api_password}';
GRANT ALL PRIVILEGES ON \`onfilm_api\`.* TO 'onfilm_api_app'@'%';

CREATE USER IF NOT EXISTS 'onfilm_worker_app'@'%' IDENTIFIED BY '${worker_password}';
ALTER USER 'onfilm_worker_app'@'%' IDENTIFIED BY '${worker_password}';
GRANT ALL PRIVILEGES ON \`onfilm_worker\`.* TO 'onfilm_worker_app'@'%';
SQL
