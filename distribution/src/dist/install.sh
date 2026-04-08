#!/bin/sh
#
# Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Install PAL to a system directory.
#
# Usage:
#   ./install.sh                      # install to /usr/local (may need sudo)
#   ./install.sh --prefix=~/.local    # install to ~/.local
#   ./install.sh --prefix=/opt/pal    # install to /opt/pal
#
# This copies the PAL distribution to PREFIX/lib/pal/ and creates a symlink
# at PREFIX/bin/pal so the 'pal' command is available on your PATH.
#
# To uninstall: rm -rf PREFIX/lib/pal PREFIX/bin/pal

set -eu

PREFIX="/usr/local"

usage() {
  echo "Usage: $0 [--prefix=DIR]"
  echo ""
  echo "Install PAL to a system directory."
  echo ""
  echo "Options:"
  echo "  --prefix=DIR    Installation prefix (default: /usr/local)"
  echo "  -h, --help      Show this help message"
  echo ""
  echo "Files are installed to PREFIX/lib/pal/ with a symlink at PREFIX/bin/pal."
}

for arg in "$@"; do
  case "$arg" in
    --prefix=*) PREFIX="${arg#--prefix=}" ;;
    -h|--help)  usage; exit 0 ;;
    *)          echo "Unknown option: $arg" >&2; usage >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Verify we're in a valid distribution directory
if [ ! -f "$SCRIPT_DIR/bin/pal" ] || [ ! -d "$SCRIPT_DIR/lib" ]; then
  echo "Error: install.sh must be run from the PAL distribution directory." >&2
  exit 1
fi

PAL_LIB="$PREFIX/lib/pal"
PAL_BIN_LINK="$PREFIX/bin/pal"

echo "Installing PAL to $PAL_LIB ..."

# Clean previous installation if present
if [ -d "$PAL_LIB" ]; then
  echo "Removing previous installation at $PAL_LIB"
  rm -rf "$PAL_LIB"
fi

mkdir -p "$PAL_LIB" "$PREFIX/bin"

# Copy distribution contents
cp -r "$SCRIPT_DIR/bin" "$SCRIPT_DIR/lib" "$SCRIPT_DIR/config" "$PAL_LIB/"
[ -d "$SCRIPT_DIR/infra" ]    && cp -r "$SCRIPT_DIR/infra" "$PAL_LIB/"
[ -d "$SCRIPT_DIR/licenses" ] && cp -r "$SCRIPT_DIR/licenses" "$PAL_LIB/"

for f in README.md LICENSE AUTHORS THIRD_PARTY.md .env.pal; do
  [ -f "$SCRIPT_DIR/$f" ] && cp "$SCRIPT_DIR/$f" "$PAL_LIB/"
done

# Ensure launcher is executable
chmod 755 "$PAL_LIB/bin/pal"

# Create symlink
ln -sf "$PAL_LIB/bin/pal" "$PAL_BIN_LINK"

echo ""
echo "PAL installed successfully."
echo "  Installation: $PAL_LIB"
echo "  Symlink:      $PAL_BIN_LINK -> $PAL_LIB/bin/pal"

# Check if PREFIX/bin is in PATH
case ":${PATH:-}:" in
  *":$PREFIX/bin:"*)
    echo ""
    echo "pal is ready to use. Try: pal help"
    ;;
  *)
    echo ""
    echo "Add $PREFIX/bin to your PATH:"
    echo "  export PATH=\"$PREFIX/bin:\$PATH\""
    ;;
esac
