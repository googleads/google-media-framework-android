/**
 Copyright 2014 Google Inc. All rights reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/**
 * This file has been taken from the ExoPlayer demo project with minor modifications.
 * https://github.com/google/ExoPlayer/
 */

package com.google.android.libraries.mediaframework.exoplayerextensions;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;

import com.google.android.exoplayer.FrameworkSampleSource;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;

/**
 * A {@link ExoplayerWrapper.RendererBuilder} for streams that can be read using
 * {@link android.media.MediaExtractor}.
 */
public class DefaultRendererBuilder implements ExoplayerWrapper.RendererBuilder {

  /**
   * The maximum duration in milliseconds for which this video renderer can attempt to seamlessly
   * join an ongoing playback.
   */
  public static final int ALLOWED_JOINING_TIME_MS = 5000;

  /**
   * The maximum number of frames that can be dropped between invocations
   * of onDroppedFrames(int, long).
   */
  public static final int MAX_DROPPED_FRAME_COUNT_TO_NOTIFY = 50;
  private final Context context;
  private final Uri uri;

  public DefaultRendererBuilder(Context context, Uri uri) {
    this.context = context;
    this.uri = uri;
  }

  @Override
  public void buildRenderers(ExoplayerWrapper player,
                             ExoplayerWrapper.RendererBuilderCallback callback) {

    // Create a sample source at the given URI with two renderers (one for audio, one for video).
    FrameworkSampleSource sampleSource = new FrameworkSampleSource(context, uri, null, 2);

    // Create the video renderer from the sample source.
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
        null,
        true,
        MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT,
        ALLOWED_JOINING_TIME_MS,
        player.getMainHandler(),
        player,
        MAX_DROPPED_FRAME_COUNT_TO_NOTIFY);

    MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
        null,
        true,
        player.getMainHandler(),
        player);

    // Invoke the callback.
    TrackRenderer[] renderers = new TrackRenderer[ExoplayerWrapper.RENDERER_COUNT];
    renderers[ExoplayerWrapper.TYPE_VIDEO] = videoRenderer;
    renderers[ExoplayerWrapper.TYPE_AUDIO] = audioRenderer;
    callback.onRenderersBuilt(null, null, renderers);
  }

}