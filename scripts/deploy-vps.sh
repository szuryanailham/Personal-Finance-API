#!/usr/bin/env bash
set -euo pipefail

app_dir=${1:?Usage: deploy-vps.sh APP_DIR COMMIT_SHA}
commit=${2:?Missing tested commit SHA}
[[ "$commit" =~ ^[0-9a-f]{40}$ ]] || { echo 'Invalid commit SHA' >&2; exit 1; }
cd "$app_dir"
# Also serializes manual deployments using this script.
exec 9> .git/artoku-deploy.lock
flock -w 1800 9
[[ -f .env ]] || { echo 'Production .env is missing' >&2; exit 1; }
[[ "$(git branch --show-current)" == release/v.0.1 ]] || {
  echo 'VPS checkout must be on release/v.0.1' >&2; exit 1;
}
[[ -z "$(git status --porcelain)" ]] || {
  echo 'VPS checkout has local changes; resolve them before deploying' >&2; exit 1;
}
export GIT_TERMINAL_PROMPT=0
export GIT_SSH_COMMAND='ssh -o BatchMode=yes -o StrictHostKeyChecking=yes'
git fetch origin refs/heads/release/v.0.1:refs/remotes/origin/release/v.0.1
# Do not deploy an older queued run after a newer commit reaches the branch.
if [[ "$(git rev-parse origin/release/v.0.1)" != "$commit" ]]; then
  echo "Skipping superseded commit $commit"
  exit 0
fi
git merge --ff-only "$commit"
[[ "$(git rev-parse HEAD)" == "$commit" ]]

compose=(docker compose -f docker-compose.prod.yml)
"${compose[@]}" config --quiet
# Build before replacing the running API; a failed build leaves it running.
"${compose[@]}" build api
"${compose[@]}" up -d --no-build --wait --wait-timeout 180

# Spring MVC returns 405 for GET on this POST-only public route once started.
# This is an HTTP startup smoke check, not a complete dependency health check.
for attempt in {1..60}; do
  status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --max-time 3 http://127.0.0.1:8080/api/auth/login || true)
  if [[ "$status" == 405 ]]; then
    echo "Deployed $commit; API responds on localhost:8080"
    exit 0
  fi
  sleep 3
done
echo 'API did not pass the HTTP startup smoke check' >&2
"${compose[@]}" ps
exit 1
