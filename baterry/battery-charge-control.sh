#!/system/bin/sh

BATTERY_DIR="${BATTERY_DIR:-/sys/class/power_supply/battery}"
CAPACITY_FILE="$BATTERY_DIR/capacity"
SLATE_FILE="$BATTERY_DIR/batt_slate_mode"

LOW_LIMIT="${LOW_LIMIT:-40}"
HIGH_LIMIT="${HIGH_LIMIT:-80}"
CHECK_INTERVAL="${CHECK_INTERVAL:-60}"
RUN_ONCE="${RUN_ONCE:-0}"
TAG="battery-charge-control"

LOG_FILE="${LOG_FILE:-/data/local/tmp/battery-charge-control.log}"
LOG_BACKUP_FILE="${LOG_BACKUP_FILE:-${LOG_FILE}.1}"
LOG_MAX_BYTES="${LOG_MAX_BYTES:-65536}"
STATUS_FILE="${STATUS_FILE:-/storage/emulated/0/Android/data/cz.hykel.hakiosk/files/battery-charge-control.status}"

sleep_pid=""

rotate_file_log() {
    [ -f "$LOG_FILE" ] || return 0

    log_size="$(wc -c < "$LOG_FILE" 2>/dev/null)"
    case "$log_size" in
        ''|*[!0-9]*)
            return 0
            ;;
    esac

    if [ "$log_size" -ge "$LOG_MAX_BYTES" ]; then
        rm -f "$LOG_BACKUP_FILE"
        mv "$LOG_FILE" "$LOG_BACKUP_FILE"
        : > "$LOG_FILE"
        chmod 0600 "$LOG_FILE" "$LOG_BACKUP_FILE" 2>/dev/null
    fi
}

log_msg() {
    priority="$1"
    shift
    message="$*"

    /system/bin/log -p "$priority" -t "$TAG" "$message"
    rotate_file_log
    echo "$(date): $message" >> "$LOG_FILE"
    chmod 0600 "$LOG_FILE" 2>/dev/null
}

clear_published_status() {
    rm -f "$STATUS_FILE" "${STATUS_FILE}.tmp" 2>/dev/null
}

publish_status() {
    slate_mode="$1"
    status_dir="${STATUS_FILE%/*}"
    [ -d "$status_dir" ] || return 0

    {
        echo "version=1"
        echo "updated_epoch_seconds=$(date +%s)"
        echo "slate_mode=$slate_mode"
    } > "${STATUS_FILE}.tmp" || return 0

    chmod 0644 "${STATUS_FILE}.tmp" 2>/dev/null
    mv -f "${STATUS_FILE}.tmp" "$STATUS_FILE" 2>/dev/null
}

sleep_interval() {
    sleep "$CHECK_INTERVAL" &
    sleep_pid=$!
    wait "$sleep_pid"
    sleep_pid=""
}

terminate() {
    if [ -n "$sleep_pid" ]; then
        kill "$sleep_pid" 2>/dev/null
        wait "$sleep_pid" 2>/dev/null
        sleep_pid=""
    fi
    clear_published_status
    log_msg i "Služba ukončena"
    exit 0
}

control_once() {
    if [ ! -r "$CAPACITY_FILE" ] || [ ! -r "$SLATE_FILE" ] || [ ! -w "$SLATE_FILE" ]; then
        clear_published_status
        log_msg e "Chyba: sysfs soubory nejsou dostupné"
        return
    fi

    capacity="$(cat "$CAPACITY_FILE" 2>/dev/null)"
    slate="$(cat "$SLATE_FILE" 2>/dev/null)"

    case "$capacity" in
        ''|*[!0-9]*)
            clear_published_status
            log_msg e "Chyba: neplatná kapacita: $capacity"
            return
            ;;
    esac

    case "$slate" in
        0|1)
            ;;
        *)
            clear_published_status
            log_msg e "Chyba: neplatná hodnota batt_slate_mode: $slate"
            return
            ;;
    esac

    if [ "$capacity" -ge "$HIGH_LIMIT" ] && [ "$slate" = "0" ]; then
        if echo 1 > "$SLATE_FILE" && [ "$(cat "$SLATE_FILE" 2>/dev/null)" = "1" ]; then
            slate="1"
            log_msg i "Nabíjení vypnuto při ${capacity} %"
        else
            clear_published_status
            log_msg e "Chyba: nepodařilo se vypnout nabíjení"
            return
        fi
    elif [ "$capacity" -le "$LOW_LIMIT" ] && [ "$slate" = "1" ]; then
        if echo 0 > "$SLATE_FILE" && [ "$(cat "$SLATE_FILE" 2>/dev/null)" = "0" ]; then
            slate="0"
            log_msg i "Nabíjení zapnuto při ${capacity} %"
        else
            clear_published_status
            log_msg e "Chyba: nepodařilo se zapnout nabíjení"
            return
        fi
    fi

    publish_status "$slate"
}

trap terminate TERM INT

log_msg i "Služba spuštěna: rozsah ${LOW_LIMIT}-${HIGH_LIMIT} %, interval ${CHECK_INTERVAL} s"

while true; do
    control_once
    [ "$RUN_ONCE" = "1" ] && exit 0
    sleep_interval
done
