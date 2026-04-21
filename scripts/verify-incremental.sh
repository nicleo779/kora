#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME_INPUT="${1:-${JAVA_HOME:-}}"

if [[ -z "${JAVA_HOME_INPUT}" ]]; then
  echo "Usage: $0 /path/to/jdk21"
  exit 1
fi

export JAVA_HOME="${JAVA_HOME_INPUT}"
export PATH="${JAVA_HOME}/bin:${PATH}"

MAPPER_FILE="${ROOT_DIR}/simple/src/main/java/com/example/simple/mapper/UserMapper.java"
ENTITY_FILE="${ROOT_DIR}/simple/src/main/java/com/example/simple/entity/User.java"

mapper_backup="$(mktemp)"
entity_backup="$(mktemp)"
cp "${MAPPER_FILE}" "${mapper_backup}"
cp "${ENTITY_FILE}" "${entity_backup}"

cleanup() {
  cp "${mapper_backup}" "${MAPPER_FILE}"
  cp "${entity_backup}" "${ENTITY_FILE}"
  rm -f "${mapper_backup}" "${entity_backup}"
}
trap cleanup EXIT

append_probe() {
  local file="$1"
  python3 - "$file" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
if path.name == "User.java":
    needle = "public class User extends BaseUser<Long> {\n"
    text = text.replace(needle, needle + "    // incremental probe\n", 1)
else:
    needle = "public interface UserMapper extends ReadMapper<User>, WriteMapper<User> {\n"
    text = text.replace(needle, needle + "    // incremental probe\n", 1)
path.write_text(text)
PY
}

echo "== Baseline compile =="
(cd "${ROOT_DIR}" && bash gradlew :simple:compileJava)

echo "== Mapper-only incremental compile =="
append_probe "${MAPPER_FILE}"
(cd "${ROOT_DIR}" && bash gradlew :simple:compileJava --info | tee /tmp/kora-mapper-incremental.log)
grep -q "Incremental compilation" /tmp/kora-mapper-incremental.log
cp "${mapper_backup}" "${MAPPER_FILE}"

echo "== Entity-only incremental compile =="
append_probe "${ENTITY_FILE}"
(cd "${ROOT_DIR}" && bash gradlew :simple:compileJava --info | tee /tmp/kora-entity-incremental.log)
grep -q "Incremental compilation" /tmp/kora-entity-incremental.log
cp "${entity_backup}" "${ENTITY_FILE}"

echo "== Verify generated artifacts =="
test -f "${ROOT_DIR}/simple/build/generated/sources/annotationProcessor/java/main/gen/KoraSimpleConfigGenerated.java"
test -f "${ROOT_DIR}/simple/build/classes/java/main/gen/kora-generated.idx"
test -f "${ROOT_DIR}/simple/build/classes/java/main/gen/kora-scan.idx"

echo "Incremental verification finished."
