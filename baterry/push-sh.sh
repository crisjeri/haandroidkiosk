#!/bin/bash

adb root
adb remount

adb push battery-charge-control.sh \
    /system/bin/battery-charge-control.sh

adb shell chmod 0755 \
    /system/bin/battery-charge-control.sh

adb shell chown root:root \
    /system/bin/battery-charge-control.sh

adb shell restorecon \
    /system/bin/battery-charge-control.sh

adb shell ls -lZ \
    /system/bin/battery-charge-control.sh

adb reboot
adb wait-for-device
adb root
