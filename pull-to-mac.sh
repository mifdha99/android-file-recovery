#!/bin/bash
# Pulls deleted / lost files from the phone straight to this Mac over USB (adb).
# Read-only on the phone: nothing is deleted, moved or renamed there.
#
#   ./pull-to-mac.sh              everything: app's RecoveredFiles + trash, bins, thumbnails, hidden, cache
#   ./pull-to-mac.sh trash        only system trash + recycle bins (full quality, fastest)
#   ./pull-to-mac.sh recovered    only the RecoveredFiles folder written by the app
#   ./pull-to-mac.sh all ~/Backup custom destination (default: ~/Desktop/PhoneRecovery/<date>)
set -euo pipefail

MODE="${1:-all}"
DEST="${2:-$HOME/Desktop/PhoneRecovery/$(date +%Y-%m-%d_%H-%M-%S)}"
ADB="$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")"
LIST=/data/local/tmp/recovery-list.txt

case "$MODE" in all|trash|recovered) ;; *) echo "Usage: $0 [all|trash|recovered] [destination]"; exit 1 ;; esac
[ -x "$ADB" ] || { echo "adb not found. Install Android platform-tools."; exit 1; }

STATE="$("$ADB" get-state 2>/dev/null || true)"
if [ "$STATE" != "device" ]; then
    echo "No phone detected."
    echo "1. Phone: Settings > Developer options > USB debugging ON"
    echo "2. Plug USB cable, tap 'Allow' on the phone"
    "$ADB" devices
    exit 1
fi

MEDIA="-iname *.jpg -o -iname *.jpeg -o -iname *.png -o -iname *.gif -o -iname *.webp -o -iname *.heic -o -iname *.heif \
 -o -iname *.bmp -o -iname *.dng -o -iname *.mp4 -o -iname *.mov -o -iname *.3gp -o -iname *.mkv -o -iname *.webm \
 -o -iname *.avi -o -iname *.mp3 -o -iname *.m4a -o -iname *.wav -o -iname *.ogg -o -iname *.opus -o -iname *.flac \
 -o -iname *.pdf -o -iname *.doc -o -iname *.docx -o -iname *.xls -o -iname *.xlsx -o -iname *.ppt -o -iname *.pptx"

# find expressions, evaluated on the phone (toybox find). set -f there stops the shell expanding the globs.
TRASH="-name .trashed-* -o -ipath *trash*/* -o -ipath *recycle*/* -o -ipath *dustbin*/* -o -path */LOST.DIR/*"
EXTRA="-path */.thumbnails/* -size +2k \
 -o -path */.* -size +8k \( $MEDIA \) \
 -o -path ./Android/* -size +8k ! -name *.* \
 -o -ipath *cache*/* -size +8k \( $MEDIA \)"

case "$MODE" in
    recovered) EXPR="-path ./RecoveredFiles/*" ;;
    trash)     EXPR="$TRASH" ;;
    all)       EXPR="-path ./RecoveredFiles/* -o $TRASH -o $EXTRA" ;;
esac

# Internal storage plus any SD card.
ROOTS="/sdcard $("$ADB" shell 'ls /storage 2>/dev/null' | tr -d '\r' | grep -E '^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$' | sed 's|^|/storage/|' || true)"

mkdir -p "$DEST"
TOTAL=0
for ROOT in $ROOTS; do
    NAME="$(basename "$ROOT")"; [ "$NAME" = "sdcard" ] && NAME="internal"
    OUT="$DEST/$NAME"
    echo "Scanning $ROOT ..."
    COUNT="$("$ADB" shell "set -f; cd '$ROOT' && find . -type f ! -path */Music/.thumbnails/* \( $EXPR \) 2>/dev/null > $LIST; wc -l < $LIST" | tr -d '\r ')"
    echo "  $COUNT files found"
    [ "${COUNT:-0}" -gt 0 ] || continue
    mkdir -p "$OUT"
    echo "  Copying to $OUT ..."
    # One tar stream instead of thousands of separate adb pulls. exec-out is binary safe.
    # tar exits non-zero when a single file is unreadable, so its status is ignored and whatever
    # is missing afterwards is pulled file by file.
    "$ADB" exec-out "cd '$ROOT' && tar -cf - -T $LIST 2>/dev/null" | tar -xf - -C "$OUT" 2>/dev/null || true
    "$ADB" shell "cat $LIST" | tr -d '\r' | while IFS= read -r F; do
        [ -e "$OUT/$F" ] && continue
        mkdir -p "$OUT/$(dirname "$F")"
        "$ADB" pull "$ROOT/${F#./}" "$OUT/$F" </dev/null >/dev/null 2>&1 || echo "  could not read: $F"
    done
    TOTAL=$((TOTAL + COUNT))
done
"$ADB" shell "rm -f $LIST" || true

# Make the result usable in Finder: un-hide dot names, restore original names of trashed files,
# give hash-named cache files a real extension, drop cache files that are not media.
python3 - "$DEST" <<'PY'
import os, re, subprocess, sys
root = sys.argv[1]
TRASHED = re.compile(r'^\.(?:trashed|pending)-\d+-(.+)$', re.I)
EXT = {'image/jpeg': '.jpg', 'image/png': '.png', 'image/gif': '.gif', 'image/webp': '.webp', 'image/heic': '.heic',
       'image/heif': '.heic', 'image/avif': '.avif', 'image/bmp': '.bmp', 'video/mp4': '.mp4', 'video/quicktime': '.mov',
       'video/3gpp': '.3gp', 'video/x-matroska': '.mkv', 'video/webm': '.webm', 'audio/mpeg': '.mp3', 'audio/mp4': '.m4a',
       'audio/x-m4a': '.m4a', 'audio/ogg': '.ogg', 'audio/x-wav': '.wav', 'audio/flac': '.flac', 'application/pdf': '.pdf',
       'application/zip': '.zip'}

def unique(path):
    base, ext = os.path.splitext(path)
    n, out = 1, path
    while os.path.exists(out):
        out = f'{base} ({n}){ext}'; n += 1
    return out

dropped = 0
for folder, dirs, files in os.walk(root, topdown=False):
    in_bin = re.search(r'trash|recycle|dustbin|LOST\.DIR', folder, re.I)
    for name in files:
        path = os.path.join(folder, name)
        m = TRASHED.match(name)
        new = m.group(1) if m else name.lstrip('.') or name
        if '.' not in new:  # hash-named: identify by content
            mime = subprocess.run(['file', '-b', '--mime-type', path], capture_output=True, text=True).stdout.strip()
            if mime in EXT:
                new += EXT[mime]
            elif not in_bin and not m:  # unknown blob from an app cache: not a photo, drop the local copy
                os.remove(path); dropped += 1; continue
        if new != name:
            os.rename(path, unique(os.path.join(folder, new)))
    for name in dirs:
        if name.startswith('.'):
            src = os.path.join(folder, name)
            if not os.listdir(src): os.rmdir(src); continue
            os.rename(src, unique(os.path.join(folder, '_' + name.lstrip('.'))))
    if folder != root and not os.listdir(folder):
        os.rmdir(folder)
if dropped: print(f'  {dropped} non-media cache blobs skipped')
PY

echo
echo "Done. $TOTAL files found on phone."
echo "Saved: $DEST  ($(du -sh "$DEST" | cut -f1))"
open "$DEST"
