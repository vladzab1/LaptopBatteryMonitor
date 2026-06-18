#!/bin/bash

# ==========================================
# КОНФІГУРАЦІЯ (Твій правильний IP Tailscale)
# ==========================================
PHONE_IP="100.124.245.60"
PORT="8080"  # Порт, який слухає твій Android-додаток
CACHE_FILE="/tmp/last_battery_state"

# 1. Збір даних батареї
BATTERY_DIR="/sys/class/power_supply/BAT1"
if [ ! -d "$BATTERY_DIR" ]; then
    BATTERY_DIR="/sys/class/power_supply/BAT0"
fi

if [ -d "$BATTERY_DIR" ]; then
    CAPACITY=$(cat "$BATTERY_DIR/capacity" 2>/dev/null)
    STATUS=$(cat "$BATTERY_DIR/status" 2>/dev/null)
else
    CAPACITY=100
    STATUS="Unknown"
fi

# Визначаємо, чи підключена зарядка (1 - так, 0 - ні)
if [ "$STATUS" == "Charging" ] || [ "$STATUS" == "Full" ]; then
    PLUGGED=1
else
    PLUGGED=0
fi

# 2. Збір температури процесора (для Arch/CachyOS)
CPU_TEMP=0
if [ -d "/sys/class/thermal/thermal_zone0" ]; then
    for zone in /sys/class/thermal/thermal_zone*; do
        type=$(cat "$zone/type" 2>/dev/null)
        if [[ "$type" == *"pkg_temp"* ]] || [[ "$type" == *"cpu"* ]]; then
            raw_temp=$(cat "$zone/temp" 2>/dev/null)
            CPU_TEMP=$((raw_temp / 1000))
            break
        fi
    done
    if [ "$CPU_TEMP" -eq 0 ]; then
        raw_temp=$(cat "/sys/class/thermal/thermal_zone0/temp" 2>/dev/null)
        CPU_TEMP=$((raw_temp / 1000))
    fi
fi

# 3. НАДІЙНИЙ ЗБІР ГУЧНОСТІ (Твій виправлений варіант через awk)
VOLUME=$(amixer get Master | awk -F'[][]' '/Left:/ {print $2}' | tr -d '%')
[ -z "$VOLUME" ] && VOLUME=0

# 4. Отримуємо статус плеєра (Playing / Paused)
if command -v playerctl >/dev/null 2>&1; then
    P_STAT=$(playerctl status 2>/dev/null | tr '[:upper:]' '[:lower:]')
    if [ "$P_STAT" == "playing" ]; then
        PLAYER_STATUS="Playing"
    else
        PLAYER_STATUS="Paused"
    fi
else
    PLAYER_STATUS="Paused"
fi

# 5. Перевірка кешу (якщо нічого не змінилося — виходимо)
CURRENT_STATE="${CAPACITY}_${STATUS}_${PLUGGED}_${CPU_TEMP}_${VOLUME}_${PLAYER_STATUS}"

if [ -f "$CACHE_FILE" ] && [ "$(cat "$CACHE_FILE")" == "$CURRENT_STATE" ]; then
    exit 0
fi
echo "$CURRENT_STATE" > "$CACHE_FILE"

# 6. Формування JSON через jq
JSON_DATA=$(jq -n \
  --arg cap "$CAPACITY" \
  --arg stat "$STATUS" \
  --arg plug "$PLUGGED" \
  --arg temp "$CPU_TEMP" \
  --arg vol "$VOLUME" \
  --arg pstat "$PLAYER_STATUS" \
  '{
    capacity: ($cap|tonumber), 
    status: $stat, 
    plugged: ($plug == "1"), 
    cpuTemp: ($temp|tonumber),
    volume: ($vol|tonumber),
    isPlaying: ($pstat == "Playing")
  }')

# 7. Відправка на OnePlus 13T
curl -X POST -H "Content-Type: application/json" -d "$JSON_DATA" --max-time 3 "http://$PHONE_IP:$PORT/battery" >/dev/null 2>&1
