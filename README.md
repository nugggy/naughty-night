# Naughty Night

An 18+ party game suite for Android. Strictly for consenting adults.

Eleven games: Truth or Dare, Dirty Dice, Spin the Bottle, Never Have I Ever, Strip Trivia, Would You Rather and Dirty Mind, plus an after-dark kink section with Kink Challenges, Yes No Maybe, Power Play and Hotwife Roller.

## Install

Download the latest `NaughtyNight.apk` from [Releases](../../releases) and open it on your phone. Allow installs from unknown sources when asked.

## Updates

The app checks this repo's Releases for new versions. In the Updates panel at the bottom of the home screen you can choose:

- **Automatic**: checks on launch, downloads new versions and asks you to confirm the install
- **Notify me**: checks on launch and shows a banner
- **Manual**: only checks when you tap the button

The **Beta** channel also picks up pre-releases.

## Build and release

Needs JDK 17, Python and the Android SDK (build-tools 36.0.0, platform android-36). No Gradle.

```bash
./build.sh                              # builds NaughtyNight.apk from VERSION
./release.sh 1.2.0 "What's new"         # stable release
./release.sh 1.3.0-beta.1 "Notes" --beta  # beta release
```

The signing keystore is not in the repo. Updates only install if they're signed with the same key, so keep `naughtynight.keystore` and `.keystore-pass` backed up.
