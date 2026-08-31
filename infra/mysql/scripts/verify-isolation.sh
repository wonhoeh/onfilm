#!/usr/bin/env bash

set -Eeuo pipefail

: "${ONFILM_API_DB_PASSWORD:?ONFILM_API_DB_PASSWORD is required}"
: "${ONFILM_WORKER_DB_PASSWORD:?ONFILM_WORKER_DB_PASSWORD is required}"

mysql_as() {
    local user="$1"
    local password="$2"
    local database="$3"
    local statement="$4"

    MYSQL_PWD="$password" mysql \
        --protocol=tcp \
        --host=127.0.0.1 \
        --user="$user" \
        --database="$database" \
        --batch \
        --skip-column-names \
        --execute="$statement"
}

assert_access_allowed() {
    local user="$1"
    local password="$2"
    local database="$3"

    local selected_database
    selected_database="$(mysql_as "$user" "$password" "$database" 'SELECT DATABASE();')"
    if [[ "$selected_database" != "$database" ]]; then
        echo "expected $user to access $database, got: $selected_database" >&2
        exit 1
    fi

    echo "PASS: $user can access $database"
}

assert_access_denied() {
    local user="$1"
    local password="$2"
    local database="$3"

    if mysql_as "$user" "$password" "$database" 'SELECT 1;' >/dev/null 2>&1; then
        echo "expected $user to be denied access to $database" >&2
        exit 1
    fi

    echo "PASS: $user cannot access $database"
}

assert_access_allowed \
    onfilm_api_app \
    "$ONFILM_API_DB_PASSWORD" \
    onfilm_api
assert_access_denied \
    onfilm_api_app \
    "$ONFILM_API_DB_PASSWORD" \
    onfilm_worker

assert_access_allowed \
    onfilm_worker_app \
    "$ONFILM_WORKER_DB_PASSWORD" \
    onfilm_worker
assert_access_denied \
    onfilm_worker_app \
    "$ONFILM_WORKER_DB_PASSWORD" \
    onfilm_api

echo "API and Worker database isolation is valid."
