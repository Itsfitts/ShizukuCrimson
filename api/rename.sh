#!/bin/bash
set -e

# Rename directories
find . -depth -name "af" -type d -execdir bash -c '
    dir="$1"
    parent="$(dirname "$dir")"
    
    # Is it af/shizuku/server?
    if [ -d "$dir/shizuku/server" ]; then
        mkdir -p "$parent/moe/shizuku/server"
        mv "$dir/shizuku/server/"* "$parent/moe/shizuku/server/"
        # We still have other things in af/shizuku?
        if [ -d "$dir/shizuku" ] && [ "$(ls -A "$dir/shizuku")" ]; then
            mkdir -p "$parent/rikka/shizuku"
            mv "$dir/shizuku/"* "$parent/rikka/shizuku/" 2>/dev/null || true
        fi
        rm -rf "$dir"
    elif [ -d "$dir/shizuku" ]; then
        mkdir -p "$parent/rikka/shizuku"
        mv "$dir/shizuku/"* "$parent/rikka/shizuku/"
        rm -rf "$dir"
    fi
' _ {} \;

# Update file contents
find . -type f -not -path "*/.git/*" -not -path "*/build/*" -not -name "rename.sh" -exec sed -i \
    -e 's/af\.shizuku\.server/moe.shizuku.server/g' \
    -e 's/af\.shizuku\.plus/moe.shizuku.manager/g' \
    -e 's/af\.shizuku\.api/rikka.shizuku/g' \
    -e 's/af\.shizuku/rikka.shizuku/g' \
    -e 's/af\/shizuku\/server/moe\/shizuku\/server/g' \
    -e 's/af\/shizuku/rikka\/shizuku/g' \
    {} +

# There might be some specific strings for Intents or Permissions
find . -type f -not -path "*/.git/*" -not -path "*/build/*" -not -name "rename.sh" -exec sed -i \
    -e 's/moe\.shizuku\.manager\.permission\.API_V23/moe.shizuku.manager.permission.API_V23/g' \
    -e 's/rikka\.shizuku\.plus/moe.shizuku.manager/g' \
    {} +

echo "Done"