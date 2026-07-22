#!/bin/bash
adb root
adb remount

adb push battery-charge-control.rc \
    /system/etc/init/battery-charge-control.rc

adb shell chmod 0644 \
    /system/etc/init/battery-charge-control.rc

adb shell chown root:root \
    /system/etc/init/battery-charge-control.rc

adb shell restorecon \
    /system/etc/init/battery-charge-control.rc

adb reboot
adb wait-for-device
adb root
