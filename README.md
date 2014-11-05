#Google Media Framework for Android

[![Build Status](https://travis-ci.org/googleads/google-media-framework-android.svg?branch=master)](https://travis-ci.org/googleads/google-media-framework-android)

##Introduction
The Google Media Framework (GMF) is a lightweight media player designed to make video playback and integration with the Google IMA SDK on Android easier.

![Google Media Framework Android Demo](http://googleads.github.io/google-media-framework-android/gmf_android_portrait.png)

The framework is currently in beta, allowing interested developers to try it out and send feedback before we finalize the APIs and features.

##Features
- A customizable video player UI for video playback on Android
    - Logo and branding colors
    - Action buttons within video UI for other actions (ex. share or download)
    - Subtitle support
- Easily integrate the Google IMA SDK to enable advertising on your video content
- Built on top of [ExoPlayer](https://github.com/google/ExoPlayer)
    - Plays [MPEG DASH](http://en.wikipedia.org/wiki/Dynamic_Adaptive_Streaming_over_HTTP) and mp4, and easily extended to other video formats
    - Does not yet support [HLS](http://en.wikipedia.org/wiki/HTTP_Live_Streaming)

##Getting started

Clone the repository

```
git clone https://github.com/googleads/google-media-framework-android.git GoogleMediaFramework
```

Then import the project in Android Studio (or build using Gradle via `./gradlew`).

##Documentation

Please see the [Javadoc](http://googleads.github.io/google-media-framework-android/docs/)

##Wiki
For a detailed description of the project, please see the [wiki](https://github.com/googleads/google-media-framework-android/wiki).

##Where do I report issues?
Please report issues on the [issues page](../../issues).

##Support
If you have questions about the framework, you can ask them in our [google group](http://groups.google.com/d/forum/google-media-framework).

##How do I contribute?
See [CONTRIBUTING.md](./CONTRIBUTING.md) for details.

##I want to use a newer version of Exoplayer
The Exoplayer library is located in the `libs` folder of the `googlemediaframework` module of this project as `exoplayer.jar`. If you want to update Exoplayer, you need to replace this with a new JAR.

To make a new JAR, please do the following:

1) Clone [Exoplayer](https://github.com/google/ExoPlayer).

2) Run the `./gradlew jarRelease` command in the Exoplayer project.

3) Look in the `build` folder of the `library` module, there should be a file called `library.jar`. Again, this must be done in the Exoplayer project.

Then, rename `library.jar` to `exoplayer.jar` and put it into the `libs` folder of the `googlemediaframework` module of this project.

##Requirements

###Deployment
  - Android 4.1+

###Development
  - Gradle (1.12 or above)
  - Android Studio (0.8 or above)
    - Build tools version 19.1.0 (installed via SDK manager)
    - Google Play Services version 4.3.23 or higher (installed via SDK manager)
    - Google Repository (installed via SDK manager)
  - ExoPlayer (Included as a jar file)

