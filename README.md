# Google Media Framework for Android

[![Build Status](https://travis-ci.org/googleads/google-media-framework-android.svg?branch=master)](https://travis-ci.org/googleads/google-media-framework-android)

# Deprecated
On March 15, 2018, we are stopping development and support for Google Media Framework (GMF) for Android in favor of the new ExoPlayer IMA extension.

## Introduction
The Google Media Framework (GMF) is a lightweight media player designed to make video playback and integration with the Google Interactive Media Ads (IMA) SDK on Android easier.

![Google Media Framework Android Demo](http://googleads.github.io/google-media-framework-android/gmf_android_portrait.png)

The framework is currently in beta, allowing interested developers to try it out and send feedback before we finalize the APIs and features.

## Features
- A customizable video player UI for video playback on Android
    - Logo and branding colors
    - Action buttons within video UI for other actions (ex. share or download)
    - Subtitle support
- Easily integrate the Google IMA SDK to enable advertising on your video content
- Built on top of [ExoPlayer](https://github.com/google/ExoPlayer)
    - Plays [MPEG DASH](http://en.wikipedia.org/wiki/Dynamic_Adaptive_Streaming_over_HTTP), [HLS](http://en.wikipedia.org/wiki/HTTP_Live_Streaming) and mp4, and easily extended to other video formats

## Getting started

Clone the repository

```
git clone https://github.com/googleads/google-media-framework-android.git GoogleMediaFramework
```

Then import the project in Android Studio (or build using Gradle via `./gradlew`).

### Via jCenter
You can also include GMF by adding the following in your project's `build.gradle` file:

```gradle
compile 'com.google.android.libraries.mediaframework:mediaframework:X.X.X'
```
where `X.X.X` is the version. For the latest version, see the
project's [Releases][]. For more details, see the project on [Bintray][].

[Releases]: https://github.com/googleads/google-media-framework-android/releases
[Bintray]: https://bintray.com/google/google-media-framework-android/mediaframework/view

_Note:_ this installs the underlying `mediaframework` library. For the demo package with IMA
integration, please download or clone the source.

## Documentation

Please see the [Javadoc](http://googleads.github.io/google-media-framework-android/docs/)

## Wiki
For a detailed description of the project, please see the [wiki](https://github.com/googleads/google-media-framework-android/wiki).

## Where do I report issues?
Please report issues on the [issues page](../../issues).

## Support
If you have questions about the framework, you can ask them in our [google group](http://groups.google.com/d/forum/google-media-framework).

## How do I contribute?
See [CONTRIBUTING.md](./CONTRIBUTING.md) for details.

## I want to use a newer version of Exoplayer
Change the version of ExoPlayer included in the [googlemediaframework](https://github.com/googleads/google-media-framework-android/tree/master/googlemediaframework) package's `build.grade`:

```gradle
compile 'com.google.android.exoplayer:exoplayer:rX.X.X'
```
_Note:_ you may have to modify the code if any underlying ExoPlayer APIs have changed.

## Requirements

### Deployment
  - Android 4.1+

### Development
  - Gradle (1.12 or above)
  - Android Studio (0.8 or above)
    - Build tools version 19.1.0 (installed via SDK manager)
    - Google Play Services version 4.3.23 or higher (installed via SDK manager)
    - Google Repository (installed via SDK manager)
  - ExoPlayer (Included as a jar file)

