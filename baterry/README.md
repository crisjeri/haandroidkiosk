# Battery Charge Control for Samsung SM-T585

This small Android init service limits battery charging on a Samsung Galaxy Tab A 10.1 LTE (SM-T585) running LineageOS 19.1 / Android 12.

It keeps the battery approximately between 40% and 80%:

- at 80% or more, charging is disabled;
- at 40% or less, charging is enabled;
- between the limits, the current state is preserved.

The service checks the battery once per minute and starts automatically after Android reports that boot has completed.

When HA Android Kiosk is installed, the service also publishes the current `batt_slate_mode` to the app-specific external files directory. The kiosk treats this optional status as unavailable when the service is absent, stopped, unable to access sysfs, or has not refreshed the file for three minutes.

## Device compatibility

This solution is device- and kernel-specific. It requires the writable sysfs node:

```text
/sys/class/power_supply/battery/batt_slate_mode
```

On the tested SM-T585 kernel, `0` enables charging and `1` disables it. Do not install the service on another device until this behavior has been verified. Writing an incorrect sysfs node may have no effect or may cause unexpected hardware behavior.

The installation also requires:

- a `userdebug` or otherwise rooted Android build;
- working `adb root` and `adb remount`;
- the SELinux domain `u:r:su:s0` used by the supplied init configuration.

## Installation

From this directory, run:

```bash
adb root
adb remount

adb push battery-charge-control.sh /system/bin/battery-charge-control.sh
adb push battery-charge-control.rc /system/etc/init/battery-charge-control.rc

adb shell chmod 0755 /system/bin/battery-charge-control.sh
adb shell chmod 0644 /system/etc/init/battery-charge-control.rc
adb shell chown root:root \
    /system/bin/battery-charge-control.sh \
    /system/etc/init/battery-charge-control.rc
adb shell restorecon \
    /system/bin/battery-charge-control.sh \
    /system/etc/init/battery-charge-control.rc

adb reboot
```

After boot, verify the service and battery state:

```bash
adb root
adb shell getprop init.svc.battctl
adb shell 'cat /sys/class/power_supply/battery/{capacity,batt_slate_mode,status}'
```

The service state should be `running`.

## Logs

Recent events are written to Android logcat:

```bash
adb logcat -b all -d -s battery-charge-control
```

Logcat uses small circular buffers, so old startup messages may disappear. A persistent file log is also kept:

```bash
adb root
adb shell tail -20 /data/local/tmp/battery-charge-control.log
```

The file rotates at approximately 64 KiB and keeps one `.log.1` backup.

## Known limitations

- The natural 40% threshold has not yet been observed during long-term operation, although the lower-limit logic and the kernel transition were tested separately.
- This LineageOS build reports a failure when Android init creates the service process group. Normal operation and boot startup work, but `adb shell stop battctl` may remain in the `stopping` state.
- Files installed directly into `/system` may be removed by a LineageOS update and may need to be installed again.
- Kernel updates can rename the sysfs node or change its behavior. Recheck it after major system updates.
- This is not a generic Android battery-control solution and is not expected to work unchanged on other tablets.
