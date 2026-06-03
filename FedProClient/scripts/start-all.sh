#!/usr/bin/env bash
# Open one gnome-terminal window per CUIS process, positioned in a grid, each
# running its start-*.sh. The scripts wait on their upstream ports, so the
# stack self-sequences regardless of how fast the windows appear.
#
# Layout (2 columns x 3 rows):
#     +-------------+-------------+
#     | CRC         | FedPro      |
#     +-------------+-------------+
#     | cuis-server | subapp      |
#     +-------------+-------------+
#     |        publisher          |
#     +---------------------------+
#
# Positioning needs an X11 session (gnome-terminal --geometry +X+Y). Under
# Wayland GNOME the offset is ignored — the windows still open and run; the WM
# places them. Override the terminal with TERM_CMD, or screen size with
# SCREEN_W / SCREEN_H, in cuis-env.local.sh or the environment.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cuis-env.sh"

TERM_CMD="${TERM_CMD:-gnome-terminal}"
command -v "$TERM_CMD" >/dev/null 2>&1 || {
  echo "ERROR: '$TERM_CMD' not found. Set TERM_CMD to your terminal emulator." >&2
  exit 1
}

[ -n "${WAYLAND_DISPLAY:-}" ] && cat >&2 <<'EOF'
NOTE: Wayland session detected — GNOME ignores window position offsets, so the
      grid coordinates below will not be honored (windows still open and run).
      For true positioning, use an X11 (Xorg) GNOME session.
EOF

# --- Screen geometry (auto-detect on X11; override via env) ------------------
if [ -z "${SCREEN_W:-}" ] || [ -z "${SCREEN_H:-}" ]; then
  if command -v xrandr >/dev/null 2>&1; then
    read -r _w _h < <(xrandr 2>/dev/null | awk '/\*/{split($1,a,"x"); print a[1], a[2]; exit}')
  fi
fi
SCREEN_W="${SCREEN_W:-${_w:-1920}}"
SCREEN_H="${SCREEN_H:-${_h:-1080}}"
CELL_W="${CELL_W:-8}"     # approx pixels per character cell (width)
CELL_H="${CELL_H:-18}"    # approx pixels per character cell (height)
GAP="${GAP:-12}"          # pixel gap between windows

# Grid cell size in pixels (2 cols, 3 rows).
COL_W=$(( SCREEN_W / 2 ))
ROW_H=$(( SCREEN_H / 3 ))

# Launch one positioned terminal. open NAME SCRIPT COL ROW COLSPAN
open() {
  local name="$1" script="$2" col="$3" row="$4" span="${5:-1}"
  local x=$(( col * COL_W ))
  local y=$(( row * ROW_H ))
  local pxw=$(( span * COL_W - GAP ))
  local pxh=$(( ROW_H - GAP ))
  local cols=$(( pxw / CELL_W ))
  local rows=$(( pxh / CELL_H ))
  # Keep the window open after the process ends so errors stay readable.
  local inner="./$script; rc=\$?; printf '\n[%s exited (rc=%s) — press Enter to close]' '$name' \"\$rc\"; read"
  "$TERM_CMD" --window \
    --title="CUIS: $name" \
    --geometry="${cols}x${rows}+${x}+${y}" \
    --working-directory="$SCRIPT_DIR" \
    -- bash -lc "$inner" &
  # gnome-terminal hands off to its server and returns immediately; small stagger
  # so the windows register in order.
  sleep 0.3
}

banner "opening 5 terminals (${SCREEN_W}x${SCREEN_H})"
open crc         start-crc.sh         0 0
open fedpro      start-fedpro.sh      1 0
open cuis-server start-cuis-server.sh 0 1
open subapp      start-subapp.sh      1 1
open publisher   start-publisher.sh   0 2 2

echo "All five terminals launched. The fedpro window will prompt for a sudo password."
