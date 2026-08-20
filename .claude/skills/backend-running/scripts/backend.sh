#!/usr/bin/env bash
#
# Lifecycle helper for the local RecipAI backend.
#
#   backend.sh start               boot on the dev profile, wait until healthy
#   backend.sh stop                graceful shutdown (also stops the postgres container)
#   backend.sh restart             stop, then start — use after changing backend code
#   backend.sh status              is it up, which pid, where are the logs
#   backend.sh logs [n]            last n lines of the run log (default 60)
#
# Everything the app needs (the dev profile, a dummy AI key) is set here so
# callers never have to remember it. Postgres is started and stopped by the app itself
# through spring-boot-docker-compose.
#
# Overridable: RECIPAI_PORT (default 8080), RECIPAI_BOOT_TIMEOUT, RECIPAI_STOP_TIMEOUT,
# SPRING_AI_API_KEY (a real key if you need /extract/** to actually work).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
RUN_DIR="$BACKEND_DIR/target"

PORT="${RECIPAI_PORT:-8080}"
BASE_URL="http://localhost:$PORT"
HEALTH_URL="$BASE_URL/actuator/health"

# Port-scoped, so a run on an alternate port keeps its own bookkeeping instead of
# reading the default run's pidfile and misreporting it.
LOG_FILE="$RUN_DIR/backend-run-$PORT.log"
PID_FILE="$RUN_DIR/backend-run-$PORT.pid"
BOOT_TIMEOUT="${RECIPAI_BOOT_TIMEOUT:-180}"
STOP_TIMEOUT="${RECIPAI_STOP_TIMEOUT:-60}"

die() { echo "error: $*" >&2; exit 1; }
info() { echo "$*"; }

# --- state ------------------------------------------------------------------

is_healthy() {
    curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'
}

running_pgid() {
    # Echoes the process-group id of a live managed run, or nothing.
    [ -f "$PID_FILE" ] || return 0
    local pgid
    pgid="$(cat "$PID_FILE" 2>/dev/null || true)"
    [ -n "$pgid" ] || return 0
    kill -0 "$pgid" 2>/dev/null && echo "$pgid"
}

tail_log() {
    if [ -f "$LOG_FILE" ]; then
        echo "--- last ${1:-40} lines of $LOG_FILE ---" >&2
        tail -n "${1:-40}" "$LOG_FILE" >&2
    fi
}

# --- commands ---------------------------------------------------------------

cmd_start() {
    if is_healthy; then
        info "backend already UP at $BASE_URL"
        cmd_status
        return 0
    fi

    local existing
    existing="$(running_pgid)"
    if [ -n "$existing" ]; then
        die "a managed run (pgid $existing) exists but is not healthy — it may still be booting. Check '$0 logs', or '$0 stop' first."
    fi

    # Any HTTP response at all (even 401) means the port is taken by something else.
    if curl -sS --max-time 3 -o /dev/null "$BASE_URL" 2>/dev/null; then
        die "something is already listening on port $PORT but it is not a healthy backend this script manages. Free the port, or set RECIPAI_PORT."
    fi

    docker info >/dev/null 2>&1 \
        || die "the docker daemon is not reachable. The app starts its own postgres via backend/compose.yaml and cannot boot without it."

    mkdir -p "$RUN_DIR"
    : > "$LOG_FILE"

    # SPRING_AI_API_KEY has no default in application.yml, so an unset value aborts
    # context creation. A dummy is enough to boot — only /extract/** actually calls out,
    # and leaving it dummy means a runaway extraction loop fails loudly instead of
    # spending real Gemini quota.
    (
        cd "$BACKEND_DIR"
        export SPRING_PROFILES_ACTIVE=dev
        export SPRING_AI_API_KEY="${SPRING_AI_API_KEY:-dummy-key-for-local}"
        export SERVER_PORT="$PORT"
        # New session, so the whole tree (maven + the forked app JVM) can be signalled
        # as one process group on stop.
        setsid bash -c 'echo $$ > "'"$PID_FILE"'"; exec ./mvnw -q spring-boot:run' \
            >> "$LOG_FILE" 2>&1 &
    )

    info "starting backend (profile dev, port $PORT) ..."

    local waited=0
    while [ "$waited" -lt "$BOOT_TIMEOUT" ]; do
        if is_healthy; then
            info "backend UP at $BASE_URL (pgid $(cat "$PID_FILE" 2>/dev/null || echo '?'), log: $LOG_FILE)"
            grep -q "AUTHENTICATION BYPASS ENABLED" "$LOG_FILE" \
                && info "dev auth active: 'Bearer alice' is the caller alice@local.test"
            return 0
        fi
        local pgid
        pgid="$(running_pgid)"
        if [ -z "$pgid" ] && [ "$waited" -gt 5 ]; then
            tail_log 40
            rm -f "$PID_FILE"
            die "the backend process exited during startup — see the log above."
        fi
        sleep 2
        waited=$((waited + 2))
    done

    tail_log 40
    die "backend did not become healthy within ${BOOT_TIMEOUT}s. It may still be booting; raise RECIPAI_BOOT_TIMEOUT or inspect the log above."
}

cmd_stop() {
    local pgid
    pgid="$(running_pgid)"

    if [ -z "$pgid" ]; then
        rm -f "$PID_FILE"
        if is_healthy; then
            die "a backend is healthy on port $PORT but was not started by this script, so it will not be killed. Stop it wherever it was launched."
        fi
        info "backend is not running"
        return 0
    fi

    # SIGTERM to the group so Spring's shutdown hooks run — that is what stops the
    # postgres container, so a SIGKILL here would leave it behind.
    info "stopping backend (pgid $pgid) ..."
    kill -TERM -- "-$pgid" 2>/dev/null || true

    local waited=0
    while [ "$waited" -lt "$STOP_TIMEOUT" ]; do
        if ! kill -0 "$pgid" 2>/dev/null; then
            rm -f "$PID_FILE"
            info "stopped"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done

    info "graceful stop timed out after ${STOP_TIMEOUT}s, sending SIGKILL"
    kill -KILL -- "-$pgid" 2>/dev/null || true
    rm -f "$PID_FILE"
    info "killed — check 'docker ps' for a leftover backend-postgres container"
}

cmd_restart() {
    cmd_stop
    cmd_start "$@"
}

cmd_status() {
    local pgid
    pgid="$(running_pgid)"
    if is_healthy; then
        echo "UP        $BASE_URL"
    else
        echo "DOWN      $BASE_URL"
    fi
    echo "pgid      ${pgid:-none}"
    echo "log       $LOG_FILE"
    [ -n "$pgid" ] || [ ! -f "$LOG_FILE" ] || echo "          (stale log from a previous run)"
}

cmd_logs() {
    [ -f "$LOG_FILE" ] || die "no log at $LOG_FILE — the backend has not been started by this script."
    tail -n "${1:-60}" "$LOG_FILE"
}

case "${1:-}" in
    start)   shift; cmd_start "$@" ;;
    stop)    cmd_stop ;;
    restart) shift; cmd_restart "$@" ;;
    status)  cmd_status ;;
    logs)    shift; cmd_logs "$@" ;;
    *)
        echo "usage: $0 {start|stop|restart|status|logs [n]}" >&2
        exit 2
        ;;
esac
