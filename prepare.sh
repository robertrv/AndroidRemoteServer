#!/bin/sh
die () {
    echo >&2 "$@"
    exit 1
}

[ "$#" -eq 1 ] || die "1 argument required, $# provided"

port=5554
isBusy=`adb devices|grep $port|wc -l`
while [ $isBusy -gt 0 ]
do
    port=$(( $port + 2 ))
    isBusy=`adb devices|grep $port|wc -l`
done

echo "free port: $port"
emulator -port $port -avd $1 -partition-size 128 &
# Has to sleep to get proper devices return value
serial=`adb devices |grep $port|cut -f1`
while [ -z $serial ] 
do
    sleep 1
    serial=`adb devices |grep $port|cut -f1`
done
echo "serial: $serial"

adb -s $serial wait-for-device
adb -s $serial remount
adb -s $serial push ./res/raw/su /system/bin
adb -s $serial shell chmod 6755 /system/bin/su
adb -s $serial shell rm /system/xbin/su
adb -s $serial shell ln -s /system/bin/su /system/xbin/su
adb -s $serial push ./res/raw/busybox /system/bin
adb -s $serial shell chmod 6755 /system/bin/busybox
adb -s $serial install -r ./others/SuperUser.apk
adb -s $serial forward tcp:5901 tcp:5901
adb -s $serial forward tcp:5000 tcp:5000

