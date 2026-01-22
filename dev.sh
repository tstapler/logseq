#!/bin/bash
echo "Starting Logseq KMP in Continuous Build Mode..."
echo "The application will automatically restart whenever you save a source file."
echo "Press Ctrl+C to stop."
echo ""

./gradlew -t :kmp:runApp