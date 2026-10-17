#!/usr/bin/env bash
# Publishes a new version to the in-app update channel (GitHub Releases).
# Usage: ./release.sh 1.2.0 "What's new"          -> Stable channel
#        ./release.sh 1.3.0-beta.1 "Notes" --beta -> Beta channel only
set -euo pipefail
cd "$(dirname "$0")"
VER="${1:?usage: ./release.sh VERSION \"notes\" [--beta]}"
NOTES="${2:-Naughty Night $VER}"
PRE=""; [ "${3:-}" = "--beta" ] && PRE="--prerelease"
echo "$VER" > VERSION
./build.sh
git add -A && git commit -m "Release v$VER" || true
git push
gh release create "v$VER" NaughtyNight.apk --title "Naughty Night $VER" --notes "$NOTES" $PRE
echo "Released v$VER. Installed apps will pick it up on their next check."
