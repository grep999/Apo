#!/usr/bin/env bash
set -euo pipefail

# Run from the EMBERFALL repository root, once an authorized game repo exists.
MAESTRO_DIR="${MAESTRO_DIR:-../maestro}"
MAESTRO_COMMIT="e5247d950fd48a8e1e07acec68ffd04d1445ba55"

if [[ -e "$MAESTRO_DIR" && ! -e "$MAESTRO_DIR/.git" ]]; then
  echo "ERROR: $MAESTRO_DIR exists but is not a Git checkout; refusing to overwrite it." >&2
  exit 2
fi
if [[ ! -e "$MAESTRO_DIR/.git" ]]; then
  git clone https://github.com/grep999/maestro.git "$MAESTRO_DIR"
fi

git -C "$MAESTRO_DIR" fetch origin main --tags
git -C "$MAESTRO_DIR" checkout "$MAESTRO_COMMIT"
ACTUAL_COMMIT="$(git -C "$MAESTRO_DIR" rev-parse HEAD)"
if [[ "$ACTUAL_COMMIT" != "$MAESTRO_COMMIT" ]]; then
  echo "ERROR: Maestro pin mismatch: expected $MAESTRO_COMMIT, got $ACTUAL_COMMIT" >&2
  exit 3
fi

python3 -m venv "$MAESTRO_DIR/.venv"
source "$MAESTRO_DIR/.venv/bin/activate"
python -c 'import sys; assert sys.version_info >= (3, 10), "Maestro requires Python >=3.10"'
pip install -e "$MAESTRO_DIR"

# Run from the upstream checkout so Apo's local `maestro/` packet directory
# cannot shadow the installed upstream Python package during import.
pushd "$MAESTRO_DIR" >/dev/null
python -m unittest discover -s tests -v
python scripts/validate_contracts.py
# _engine_src_0.py is a generated source-string fixture, not an importable module.
python -m compileall -q -x '_engine_src_0[.]py$' maestro scripts tests
maestro adapters
maestro adapter-health --name echo
popd >/dev/null

printf '\nMaestro v0.3.0 verified at %s\n' "$ACTUAL_COMMIT"
printf 'Use maestro/tasks_v2 and maestro/packets_v03 in this project package.\n'
