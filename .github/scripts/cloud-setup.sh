#!/usr/bin/env bash
# Setup-Script für die Claude-Code-Cloud-Umgebung (claude.ai/code → Umgebung → Bearbeiten →
# „Setup script“): Inhalt dort einfügen. Installiert das Android SDK, damit Sessions ohne
# ~7 Minuten Download bauen und testen können. Fehler hier lassen die Session nicht scheitern.
set -u

SDK=/opt/android-sdk
if [ ! -d "$SDK/platforms/android-35" ]; then
  mkdir -p "$SDK/cmdline-tools" && cd /tmp \
    && curl -sSLo cmdline-tools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q -o cmdline-tools.zip \
    && rm -rf "$SDK/cmdline-tools/latest" && mv cmdline-tools "$SDK/cmdline-tools/latest" \
    && rm cmdline-tools.zip \
    && yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
      "platforms;android-35" "build-tools;34.0.0" "build-tools;35.0.0" "platform-tools" > /dev/null \
    || echo "Android SDK konnte nicht installiert werden" >&2
fi

# Gradle findet das SDK über ANDROID_HOME; local.properties ist nicht committet. Nicht jede
# Shell liest dieselbe Datei, daher an beide Stellen. Sonst hilft: echo sdk.dir=$SDK > local.properties
for rc in ~/.bashrc ~/.profile; do
  grep -q ANDROID_HOME "$rc" 2>/dev/null || echo "export ANDROID_HOME=$SDK" >> "$rc"
done
exit 0
