#!/bin/bash

kill_process() {
  local pid="$1"

  echo -n "Kill children '$pid' process: "
  pkill --count --echo --parent "$pid"
  echo -n "Kill '$pid' process"
  kill "$pid"
  echo
}

kill_all() {
  for pid in `ps -ef | grep "${PICO:COMPONENT_CONFIG_DIR}" | grep -v "grep" | awk '{print $2}'`; do
    kill_process "$pid"
  done
}

kill_component() {
  local name="$1"
  local state_file="${PICO:STATE_DIR}/${name}.json"

  if [ -f "$state_file" ]; then
    local pid
    pid=$( cat "$state_file" | sed 's/.*pid":\(\w*\),.*/\1/')
    kill_process "$pid"
  else
    echo "Status file for '$name' component doesn't exist, file: '$state_file'"
  fi
}

flag=$1
param=$2

if [ "$flag" == '-p' ] || [ "$flag" == '--process' ]; then
  kill_process "$param"
elif [ "$flag" == '-c' ] || [ "$flag" == '--component' ]; then
  kill_component "$param"
else
  kill_all
fi