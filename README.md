# Naughty Night

An 18+ party game suite for Android. Strictly for consenting adults.

Nineteen games: Truth or Dare, Dirty Dice, Spin the Bottle, Never Have I Ever, Strip Trivia, Would You Rather and Dirty Mind. For couples there's Love Map, Massage Deck and Sensation Roulette. The after-dark kink section has Kink Challenges, Yes No Maybe, Power Play, Hotwife Roller, Fantasy Builder, Tease Timer, Slow Burn, Scene Generator and Positions.

Also included:

- **Date Night** chains three or four games into one planned evening
- **Challenge Streaks** give you a dare a day for a week, building up to a planned night
- **My Cards** lets you write your own truths, dares, kink challenges, power orders and tease moves (stored on the phone only)
- **Heat dial** on the home screen caps every tiered deck at Mild, Spicy or Wild
- **Limits**: anything either partner answers No to in Yes, No, Maybe is hidden from every deck
- **Swap card** and **Pause** buttons on every game screen

Photos are from [Unsplash](https://unsplash.com) under the Unsplash Licence (see `app/assets/img/CREDITS.txt`). Position illustrations are original SVG.

## Install

Download the latest `NaughtyNight.apk` from [Releases](../../releases) and open it on your phone. Allow installs from unknown sources when asked.

## Updates

The app checks this repo's Releases for new versions. In the Updates panel at the bottom of the home screen you can choose:

- **Automatic**: checks on launch, downloads new versions and asks you to confirm the install
- **Notify me**: checks on launch and shows a banner
- **Manual**: only checks when you tap the button

The **Beta** channel also picks up pre-releases.

## Build and release

Needs JDK 17 and the Android SDK (build-tools 36.0.0, platform android-36). No Gradle.

```bash
./build.sh                              # builds NaughtyNight.apk from VERSION
./release.sh 1.2.0 "What's new"         # stable release
./release.sh 1.3.0-beta.1 "Notes" --beta  # beta release
```

The signing keystore is not in the repo. Updates only install if they're signed with the same key, so keep `naughtynight.keystore` and `.keystore-pass` backed up.
