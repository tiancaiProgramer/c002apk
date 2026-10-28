#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash gradlew :app:assembleRelease -PreleaseVersionName="${RELEASE_VERSION_NAME:-0.1.5}" -PreleaseVersionCode="${RELEASE_VERSION_CODE:-270}" "$@"
python3 - <<'PYBUILD'
import json, pathlib, shutil, hashlib
folder = pathlib.Path('app/build/outputs/apk/release')
metadata = json.loads((folder/'output-metadata.json').read_text())
apk = folder/metadata['elements'][0]['outputFile']
out = pathlib.Path('dist'); out.mkdir(exist_ok=True)
element = metadata['elements'][0]
if apk.stat().st_size > 10 * 1024 * 1024:
    raise SystemExit('Release APK exceeds 10 MiB. Check R8/resource shrinking and avoid debug or -PdisableR8=true builds.')
target = out/f"c002apk-{element['versionName']}.apk"
shutil.copy2(apk, target)
(out/'SHA256SUMS').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
print(f'Built {target} ({target.stat().st_size / 1000000:.2f} MB, R8 enabled by default)')
PYBUILD
