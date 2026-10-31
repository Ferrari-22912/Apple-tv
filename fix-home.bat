@echo off
rem Run this if the Google TV screen comes back after a reboot.
adb connect 192.168.1.12:5555
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb shell cmd package set-home-activity com.example.tvhome/.MainActivity
adb shell input keyevent KEYCODE_HOME
