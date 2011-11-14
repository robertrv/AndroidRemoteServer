#!/bin/sh
die () {
    echo >&2 "$@"
    exit 1
}

[ "$#" -eq 1 ] || die "1 argument required, $# provided"

emulator -avd $1 -partition-size 128 &
adb -e wait-for-device
adb -e remount
adb -e push ./res/raw/su /system/bin
adb -e shell chmod 6755 /system/bin/su
adb -e shell rm /system/xbin/su
adb -e shell ln -s /system/bin/su /system/xbin/su
adb -e push ./res/raw/busybox /system/bin
adb -e shell chmod 6755 /system/bin/busybox
adb -e install ./others/SuperUser.apk
adb forward tcp:5901 tcp:5901

