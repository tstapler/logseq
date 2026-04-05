# Wireless Android Debugging Skill

This skill provides step-by-step instructions for setting up Android Wireless Debugging for the Logseq KMP project.

## 1. Prerequisites

- **Developer Options Enabled**: Ensure **Developer Options** is enabled on your Android device.
- **Same Network**: Computer and Android device MUST be on the same Wi-Fi network.
- **ADB Installed**: Ensure `adb` is in your PATH (e.g., `~/Android/Sdk/platform-tools/adb`).

## 2. Pairing Your Device (One-Time)

1.  On your phone, go to **Developer Options > Wireless Debugging**.
2.  Turn on **Wireless Debugging**.
3.  Tap on **Wireless Debugging** (the text, not the switch).
4.  Tap **Pair device with pairing code**.
5.  On your computer, run:
    ```bash
    adb pair <IP_ADDRESS>:<PAIRING_PORT> <PAIRING_CODE>
    ```
    *Note: Use the port and code shown on the pairing dialog.*

## 3. Connecting Your Device (Per-Session)

1.  Look at the **main Wireless Debugging screen** for the **IP address & Port** (under the toggle).
    *Note: This port is usually different from the pairing port.*
2.  On your computer, run:
    ```bash
    adb connect <IP_ADDRESS>:<CONNECTION_PORT>
    ```
3.  Verify the connection:
    ```bash
    adb devices
    ```

## 4. Building and Installing

Use the provided script to build and install. If multiple devices are connected, set the `ADB_DEVICE_ID` environment variable:

```bash
export ADB_DEVICE_ID="<YOUR_DEVICE_IP>:<YOUR_DEVICE_PORT>"
./run-android.sh
```

## 5. Troubleshooting

- **Timeout**: Pairing codes expire quickly. If it fails, generate a new one.
- **Connection Refused**: Ensure Wireless Debugging is still toggled ON on your phone.
- **Multiple Devices**: If you see `adb: more than one device/emulator`, always use `adb -s <DEVICE_ID>`.
