# bleed
A simple iBeacon trasmitter for Android

## Build
1. Install JDK or Android Studio

(On terminal)

2. Build app
    ```bash
    ./gradlew assembleDebug
    ```
3. Install and run app
    ```bash
    ./gradlew installDebug
    ```
4. (Optional) You can install app from apk file (located on `app/build/outputs/apk/debug/app-debug.apk`)

(On Android Stduion)

2. Build and install app with `Run` button on upper right of the window

## Usage
1. Launch app (name: 'bleed')
2. Set UUID, Major, Minor, TX Power
3. Tap '送信開始'
4. Wait for starting transmitter service
    * When service starts, a notification (looks 'i' icon) is sended
5. You can kill the app, and the service works on background
6. To stop service, launch this app or tap the notification and tap '送信停止'
