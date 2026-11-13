#!/usr/bin/env bash
# 把示例插件打包成可直接导入的 .zip（每个目录一个包）
set -e
cd "$(dirname "$0")"
for d in */; do
  [ -f "$d/plugin.json" ] || continue
  name="${d%/}"
  rm -f "$name.zip"
  ( cd "$d" && zip -qr "../$name.zip" . )
  echo "built $name.zip"
done
