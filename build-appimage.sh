#!/bin/bash
# Build a self-contained AppImage for Logseq
# This creates a single executable file that contains everything

set -e

echo "🚀 Building Logseq AppImage..."

APP_NAME="Logseq"
cd "$(dirname "$0")"

# Create temporary build directory
BUILDDIR=$(mktemp -d)
APPDIR="$BUILDDIR/AppDir"
mkdir -p "$APPDIR/usr/bin"
mkdir -p "$APPDIR/usr/lib"

echo "📦 Building Kotlin classes..."
./gradlew :kmp:classes --no-daemon -q

echo "📁 Copying application files..."
# Copy compiled classes
cp -r build/classes/kotlin/main/* "$APPDIR/usr/lib/"

# Copy all runtime dependencies
echo "📁 Copying dependencies..."
GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"

# Find and copy all required JARs
find "$GRADLE_CACHE" -name "*.jar" 2>/dev/null | while read jar; do
    # Skip sources and javadocs
    [[ "$jar" == *"-sources.jar" ]] && continue
    [[ "$jar" == *"-javadoc.jar" ]] && continue
    [[ "$jar" == *"-tests.jar" ]] && continue
    
    cp -f "$jar" "$APPDIR/usr/lib/" 2>/dev/null || true
done

# Create wrapper script
cat > "$APPDIR/AppRun" << 'WRAPPER'
#!/bin/bash
# Logseq AppImage wrapper
# Sets up environment and runs the application

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export LD_LIBRARY_PATH="$SCRIPT_DIR/usr/lib:$LD_LIBRARY_PATH"
export SKIKO_RENDERER=OPENGL

cd "$SCRIPT_DIR"
exec java -cp "usr/lib/*" com.logseq.kmp.MainKt "$@"
WRAPPER
chmod +x "$APPDIR/AppRun"

# Copy desktop file
if [ -f flatpak/logseq.desktop ]; then
    cp flatpak/logseq.desktop "$APPDIR/"
fi

# Copy or create icon
if [ -f resources/icons/icon.png ]; then
    mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"
    cp resources/icons/icon.png "$APPDIR/usr/share/icons/hicolor/256x256/apps/com.logseq.app.png"
    cp resources/icons/icon.png "$APPDIR/.DirIcon"
fi

# Create the AppImage using the squashfs approach
echo "🔨 Creating AppImage..."

# Get size
SIZE=$(du -sb "$APPDIR" | cut -f1)

# Create a minimal filesystem header
mkdir -p "$BUILDDIR/squashfs-root"

# Use the appdir2squat.sh script approach - create a self-extracting archive
cd "$BUILDDIR"

# Create the main executable
cat > "$APP_NAME-x86_64.AppImage" << 'MAINEXEC'
#!/bin/bash
# Logseq - Privacy-first knowledge management
# 
# To run: ./Logseq-x86_64.AppImage

set -e

SELF=$(readlink -f "$0")
APPDIR="${SELF%.AppImage}.AppDir"

if [ ! -d "$APPDIR" ]; then
    # Extract AppImage
    echo "Extracting Logseq..."
    mkdir -p "$APPDIR"
    tail -n +9 "$SELF" | tar -xzf - -C "$APPDIR"
fi

export LD_LIBRARY_PATH="$APPDIR/usr/lib:$LD_LIBRARY_PATH"
export SKIKO_RENDERER=OPENGL

cd "$APPDIR"
exec ./AppRun "$@"
MAINEXEC

# Make it executable
chmod +x "$APP_NAME-x86_64.AppImage"

# Append the AppDir contents (skip the first 8 lines which are the shebang)
tar -czf - -C "$BUILDDIR/AppDir" . >> "$APP_NAME-x86_64.AppImage"

# Add trailer marker
echo "" >> "$APP_NAME-x86_64.AppImage"

# Clean up
rm -rf "$BUILDDIR"

echo ""
echo "✅ AppImage created: $APP_NAME-x86_64.AppImage"
echo ""
echo "File size: $(ls -lh $APP_NAME-x86_64.AppImage | cut -d' ' -f5)"
echo ""
echo "To run:"
echo "  chmod +x $APP_NAME-x86_64.AppImage"
echo "  ./$APP_NAME-x86_64.AppImage"
echo ""
echo "To install system-wide:"
echo "  sudo mv $APP_NAME-x86_64.AppImage /usr/local/bin/logseq"
echo "  logseq"
