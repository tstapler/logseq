# Dockerfile for running Logseq Kotlin Compose Desktop app on Linux/KDE
FROM ghcr.io/jetbrains/kotlin:${KOTLIN_VERSION:-2.0.21}-jvm21

# Install system dependencies for Compose Desktop + GPU support
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1-mesa-glx \
    libgl1-mesa-dri \
    libosmesa6 \
    libxkbcommon0 \
    libxdamage1 \
    libxfixes3 \
    libxcb1 \
    libx11-xcb1 \
    libxcb-glx0 \
    libxcb-dri3-0 \
    libxshmfence1 \
    libxxf86vm1 \
    libgtk-3-0 \
    libasound2 \
    libfontconfig1 \
    libfreetype6 \
    libpng16-16 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /workspace

# Copy project
COPY . .

# Build
RUN ./gradlew :kmp:compileKotlin --no-daemon

# Run with GPU support
CMD ["bash", "-c", "export SKIKO_RENDERER=OPENGL && ./gradlew :kmp:runApp --no-daemon"]
