# Debiandroid

Run Debian on Android.

Was made to replace my [Debiandroid](https://github.com/donno2048/Debiandroid) project.

## Build

The Debian rootfs is selected automatically from the ABI.

Possible ABIs: arm64-v8a, armeabi-v7a, x86_64, x86.

For example:

```sh
./gradlew clean assembleDebug -Pabi=arm64-v8a
```
