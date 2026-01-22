#!/bin/bash
# Flatpak Build Script for Logseq
# This script builds the application and creates a Flatpak package

set -e

echo "🚀 Building Logseq for Flatpak..."

# Change to project root
cd "$(dirname "$0")/../.."

# Check Java version
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install JDK 21."
    exit 1
fi

# Build with Gradle
echo "📦 Building with Gradle..."
./gradlew :kmp:package --no-daemon

# Check if build succeeded
if [ ! -d "kmp/build/compose" ]; then
    echo "❌ Build failed. Compose distribution not found."
    exit 1
fi

# Find the distribution directory
DIST_DIR=$(find kmp/build/compose -name "kmp-*" -type d | head -1)
if [ -z "$DIST_DIR" ]; then
    echo "❌ Distribution directory not found."
    exit 1
fi

echo "📦 Found distribution: $DIST_DIR"

# Create Flatpak build directory
FLATPAK_DIR="flatpak-build"
rm -rf "$FLATPAK_DIR"
mkdir -p "$FLATPAK_DIR/opt"
mkdir -p "$FLATPAK_DIR/bin"
mkdir -p "$FLATPAK_DIR/share/applications"
mkdir -p "$FLATPAK_DIR/share/icons/hicolor/256x256/apps"

# Copy application
cp -r "$DIST_DIR" "$FLATPAK_DIR/opt/logseq"

# Create wrapper script
cat > "$FLATPAK_DIR/bin/logseq" << 'WRAPPER'
#!/bin/bash
cd /opt/logseq
./logseq "$@"
WRAPPER
chmod +x "$FLATPAK_DIR/bin/logseq"

# Create desktop file
cat > "$FLATPAK_DIR/share/applications/com.logseq.app.desktop" << 'DESKTOP'
[Desktop Entry]
Name=Logseq
Comment=A privacy-first knowledge management platform
Exec=logseq %f
Terminal=false
Type=Application
Categories=Office;Productivity;
Icon=com.logseq.app
DESKTOP

# Copy icon (create a placeholder if not exists)
if [ -f "resources/icons/icon.png" ]; then
    cp "resources/icons/icon.png" "$FLATPAK_DIR/share/icons/hicolor/256x256/apps/com.logseq.app.png"
else
    # Create a simple placeholder SVG
    cat > "$FLATPAK_DIR/share/icons/hicolor/256x256/apps/com.logseq.app.png" << 'EOF'
placeholder_icon
EOF
fi

# Create metainfo.xml for app stores
mkdir -p "$FLATPAK_DIR/share/metainfo"
cat > "$FLATPAK_DIR/share/metainfo/com.logseq.app.metainfo.xml" << 'METAINFO'
<?xml version="1.0" encoding="UTF-8"?>
<component type="desktop-application">
  <id>com.logseq.app</id>
  <name>Logseq</name>
  <summary>A privacy-first knowledge management platform</summary>
  <description>
    <p>Logseq is an open-source privacy-first knowledge management platform.</p>
    <p>Features:</p>
    <ul>
      <li>Block-based editing</li>
      <li>Bidirectional linking</li>
      <li>Full-text search</li>
      <li>Markdown/Org-mode support</li>
    </ul>
  </description>
  <categories>
    <category>Office</category>
    <category>Productivity</category>
  </categories>
  <launchable type="desktop-id">com.logseq.app.desktop</launchable>
</component>
METAINFO

# Create the Flatpak bundle
echo "📦 Creating Flatpak bundle..."
cd "$FLATPAK_DIR"
tar -cvf ../logseq.flatpak . --sort=name

echo ""
echo "✅ Build complete!"
echo ""
echo "To install the Flatpak:"
echo "  flatpak install logseq.flatpak"
echo ""
echo "To run:"
echo "  flatpak run com.logseq.app"
echo ""
echo "Or use the local installation:"
echo "  cd $FLATPAK_DIR"
echo "  flatpak-builder --install --user build-dir com.logseq.app.yaml"
