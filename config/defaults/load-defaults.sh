#!/usr/bin/env bash

TIMEOUT=3
LOOM_SERVER_HOME=${LOOM_SERVER_HOME:-/opt/loom/server}
LOOM_SERVER_URI=${LOOM_SERVER_URI:-http://localhost:55054}
LOOM_API_USER=${LOOM_API_USER:-admin}
LOOM_API_KEY=${LOOM_API_KEY:-1234567890abcdef}
LOOM_TENANT=${LOOM_TENANT:-superadmin}
MAINDIR=${LOOM_SERVER_HOME}/config/defaults

dirs="clustertemplates hardwaretypes imagetypes providers services"

if [ "x$LOOM_USE_DUMMY_PROVISIONER" == "xtrue" ]
then
  dirs="$dirs plugins/providertypes plugins/automatortypes"
fi

for d in ${dirs} ; do
  cd ${MAINDIR}
  [[ -d ${d} ]] && cd ${d} || continue
  for f in $(ls -1 *.json) ; do
    curl --silent --request PUT \
      --header "Content-Type:application/json" \
      --header "UserID:${LOOM_API_USER}" \
      --header "ApiKey:${LOOM_API_KEY}" \
      --header "TenantID:${LOOM_TENANT}" \
      --connect-timeout ${TIMEOUT} --data @${f} \
      ${LOOM_SERVER_URI}/v2/${d}/${f/.json/}
    ret=$?
    [[ ${ret} -ne 0 ]] && failed="${failed} ${d}/${f}"
  done
done
[[ ${failed} ]] && echo "Failed to load: ${failed}" && exit 1
exit 0
