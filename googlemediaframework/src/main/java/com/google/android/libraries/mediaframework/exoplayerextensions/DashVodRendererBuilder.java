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

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.UnsupportedSchemeException;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Pair;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.ChunkSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.FormatEvaluator;
import com.google.android.exoplayer.chunk.FormatEvaluator.AdaptiveEvaluator;
import com.google.android.exoplayer.chunk.MultiTrackChunkSource;
import com.google.android.exoplayer.dash.DashMp4ChunkSource;
import com.google.android.exoplayer.dash.DashWebmChunkSource;
import com.google.android.exoplayer.dash.mpd.AdaptationSet;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescription;
import com.google.android.exoplayer.dash.mpd.MediaPresentationDescriptionFetcher;
import com.google.android.exoplayer.dash.mpd.Period;
import com.google.android.exoplayer.dash.mpd.Representation;
import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer.upstream.BufferPool;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import java.util.ArrayList;

/**
 * A {@link ExoplayerWrapper.RendererBuilder} for DASH VOD.
 */
public class DashVodRendererBuilder implements ExoplayerWrapper.RendererBuilder,
    ManifestCallback<MediaPresentationDescription> {

  /**
   * The length of each buffer in the pool.
   */
  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;

  /**
   * The number of video segments in each buffer.
   */
  private static final int VIDEO_BUFFER_SEGMENTS = 200;

  /**
   * The number of audio segments in each buffer.
   */
  private static final int AUDIO_BUFFER_SEGMENTS = 60;

  /**
   * Constant indicating that the Widevine security level is unknown. This constant must be an int
   * instead of part of an enum because it has significance in the Exoplayer library. The Widevine
   * security levels are documented here:
   * https://source.android.com/devices/drm.html#security
   */
  private static final int SECURITY_LEVEL_UNKNOWN = -1;

  /**
   * Constant indicating that first Widevine security level. This constant must be an int
   * instead of part of an enum because it has significance in the Exoplayer library. The Widevine
   * security levels are documented here:
   * https://source.android.com/devices/drm.html#security
   */
  private static final int SECURITY_LEVEL_1 = 1;

  /**
   * Constant indicating that third Widevine security level. This constant must be an int
   * instead of part of an enum because it has significance in the Exoplayer library. The Widevine
   * security levels are documented here:
   * https://source.android.com/devices/drm.html#security
   */
  private static final int SECURITY_LEVEL_3 = 3;

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

  /**
   * A User-Agent string that should be sent with HTTP requests.
   */
  private final String userAgent;

  /**
   * The URL of the video that this {@link ExoplayerWrapper.RendererBuilder} will build.
   */
  private final String url;

  /**
   * The ID of the DASH video.
   */
  private final String contentId;

  /**
   * Performs {@link android.media.MediaDrm} key and provisioning requests.
   */
  private final MediaDrmCallback drmCallback;

  /**
   * Contains the {@link com.google.android.exoplayer.ExoPlayer} which will use this renderer
   * builder to play a video.
   */
  private ExoplayerWrapper player;

  /**
   * The callback that will be executed when this renderer has built successfully.
   */
  private ExoplayerWrapper.RendererBuilderCallback callback;

  /**
   * @param userAgent A User-Agent string that should be sent with HTTP requests.
   * @param url The URL of the video that this {@link ExoplayerWrapper.RendererBuilder} will build.
   * @param contentId The ID of the DASH video.
   * @param drmCallback Performs {@link android.media.MediaDrm} key and provisioning requests.
   */
  public DashVodRendererBuilder(String userAgent,
                                String url,
                                String contentId,
                                MediaDrmCallback drmCallback) {
    this.userAgent = userAgent;
    this.url = url;
    this.contentId = contentId;
    this.drmCallback = drmCallback;
  }

  @Override
  public void buildRenderers(ExoplayerWrapper player,
                             ExoplayerWrapper.RendererBuilderCallback callback) {
    this.player = player;
    this.callback = callback;

    // Fetch the manifest for the DASH file in the background.
    // The onManifestError and onManifest methods are callbacks which the manifest fetcher triggers.
    MediaPresentationDescriptionFetcher mpdFetcher = new MediaPresentationDescriptionFetcher(this);
    mpdFetcher.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url, contentId);
  }

  @Override
  public void onManifestError(String contentId, Exception e) {
    callback.onRenderersError(e);
  }

  @Override
  public void onManifest(String contentId, MediaPresentationDescription manifest) {
    Handler mainHandler = player.getMainHandler();

    // Create load control to coordinate loading of audio and video data.
    LoadControl loadControl = new DefaultLoadControl(new BufferPool(BUFFER_SEGMENT_SIZE));

    // Create a bandwidth meter to estimate bandwidth (used to adapt data rate).
    DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler, player);

    // Obtain Representations for playback.
    int maxDecodableFrameSize = MediaCodecUtil.maxH264DecodableFrameSize();
    ArrayList<Representation> audioRepresentationsList = new ArrayList<Representation>();
    ArrayList<Representation> videoRepresentationsList = new ArrayList<Representation>();
    Period period = manifest.periods.get(0);

    // Iterate through the periods, accumulating the audio and video representations, and noting
    // whether the content is protected.
    boolean hasContentProtection = false;
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      hasContentProtection |= adaptationSet.hasContentProtection();
      int adaptationSetType = adaptationSet.type;
      for (int j = 0; j < adaptationSet.representations.size(); j++) {
        Representation representation = adaptationSet.representations.get(j);
        if (adaptationSetType == AdaptationSet.TYPE_AUDIO) {
          audioRepresentationsList.add(representation);
        } else if (adaptationSetType == AdaptationSet.TYPE_VIDEO) {
          Format format = representation.format;
          if (format.width * format.height <= maxDecodableFrameSize) {
            videoRepresentationsList.add(representation);
          } else {
            // The device isn't capable of playing this stream.
          }
        }
      }
    }
    Representation[] videoRepresentations = new Representation[videoRepresentationsList.size()];
    videoRepresentationsList.toArray(videoRepresentations);

    // Check drm support if necessary.
    DrmSessionManager drmSessionManager = null;
    if (hasContentProtection) {
      if (Util.SDK_INT < 18) {
        callback.onRenderersError(new UnsupportedOperationException(
            "Protected content not supported on API level " + Util.SDK_INT));
        return;
      }
      try {
        Pair<DrmSessionManager, Boolean> drmSessionManagerData =
            V18Compat.getDrmSessionManagerData(player, drmCallback);
        drmSessionManager = drmSessionManagerData.first;
        if (!drmSessionManagerData.second) {
          // HD streams require L1 security.
          videoRepresentations = getSdRepresentations(videoRepresentations);
        }
      } catch (Exception e) {
        callback.onRenderersError(e);
        return;
      }
    }

    // Build the video data source.
    DataSource videoDataSource = new HttpDataSource(userAgent,
        null,
        bandwidthMeter);
    ChunkSource videoChunkSource;
    String mimeType = videoRepresentations[0].format.mimeType;

    // Pick the chunk source based on the MIME type of the content.
    if (mimeType.equals(MimeTypes.VIDEO_MP4)) {
      videoChunkSource = new DashMp4ChunkSource(videoDataSource,
          new AdaptiveEvaluator(bandwidthMeter),
          videoRepresentations);
    } else if (mimeType.equals(MimeTypes.VIDEO_WEBM)) {
      // TODO: Figure out how to query supported vpX resolutions. For now, restrict to standard
      // definition streams.
      videoRepresentations = getSdRepresentations(videoRepresentations);
      videoChunkSource = new DashWebmChunkSource(videoDataSource,
          new AdaptiveEvaluator(bandwidthMeter),
          videoRepresentations);
    } else {
      throw new IllegalStateException("Unexpected mime type: " + mimeType);
    }

    // Create the sample source and, finally, create the video renderer.
    ChunkSampleSource videoSampleSource = new ChunkSampleSource(videoChunkSource,
        loadControl,
        VIDEO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE,
        true,
        mainHandler,
        player,
        ExoplayerWrapper.TYPE_VIDEO);
    MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(videoSampleSource,
        drmSessionManager,
        true,
        MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT,
        ALLOWED_JOINING_TIME_MS,
        mainHandler,
        player,
        MAX_DROPPED_FRAME_COUNT_TO_NOTIFY);

    // Next, we will work on the audio renderer.
    final String[] audioTrackNames;
    final MultiTrackChunkSource audioChunkSource;
    final MediaCodecAudioTrackRenderer audioRenderer;

    // Handle the case where there is no audio.
    if (audioRepresentationsList.isEmpty()) {
      audioTrackNames = null;
      audioChunkSource = null;
      audioRenderer = null;
    } else {

      // Create the audio data source.
      DataSource audioDataSource = new HttpDataSource(userAgent,
          HttpDataSource.REJECT_PAYWALL_TYPES,
          bandwidthMeter);

      // Create array of number of channels and sampling rate for each representation.
      // This is the audio track names.
      audioTrackNames = new String[audioRepresentationsList.size()];
      ChunkSource[] audioChunkSources = new ChunkSource[audioRepresentationsList.size()];
      FormatEvaluator audioEvaluator = new FormatEvaluator.FixedEvaluator();
      for (int i = 0; i < audioRepresentationsList.size(); i++) {
        Representation representation = audioRepresentationsList.get(i);
        Format format = representation.format;
        audioTrackNames[i] = format.id + " (" + format.numChannels + "ch, " +
            format.audioSamplingRate + "Hz)";
        audioChunkSources[i] = new DashMp4ChunkSource(audioDataSource,
            audioEvaluator, representation);
      }


      // Create the audio sample source and ultimately the audio renderer.
      audioChunkSource = new MultiTrackChunkSource(audioChunkSources);
      SampleSource audioSampleSource = new ChunkSampleSource(audioChunkSource,
          loadControl,
          AUDIO_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE,
          true,
          mainHandler,
          player,
          ExoplayerWrapper.TYPE_AUDIO);
      audioRenderer = new MediaCodecAudioTrackRenderer(audioSampleSource,
          drmSessionManager,
          true,
          mainHandler,
          player);
    }


    // Invoke the callback.
    String[][] trackNames = new String[ExoplayerWrapper.RENDERER_COUNT][];
    trackNames[ExoplayerWrapper.TYPE_AUDIO] = audioTrackNames;

    MultiTrackChunkSource[] multiTrackChunkSources =
        new MultiTrackChunkSource[ExoplayerWrapper.RENDERER_COUNT];
    multiTrackChunkSources[ExoplayerWrapper.TYPE_AUDIO] = audioChunkSource;

    TrackRenderer[] renderers = new TrackRenderer[ExoplayerWrapper.RENDERER_COUNT];
    renderers[ExoplayerWrapper.TYPE_VIDEO] = videoRenderer;
    renderers[ExoplayerWrapper.TYPE_AUDIO] = audioRenderer;
    callback.onRenderersBuilt(trackNames, multiTrackChunkSources, renderers);
  }

  /**
   * Iterate through each of the representations and return an array of all representations which
   * are standard definition (less than 720 x 1280).
   * @param representations A set of representations which include the format widths and heights.
   */
  private Representation[] getSdRepresentations(Representation[] representations) {
    ArrayList<Representation> sdRepresentations = new ArrayList<Representation>();
    for (int i = 0; i < representations.length; i++) {
      if (representations[i].format.height < 720 && representations[i].format.width < 1280) {
        sdRepresentations.add(representations[i]);
      }
    }
    Representation[] sdRepresentationArray = new Representation[sdRepresentations.size()];
    sdRepresentations.toArray(sdRepresentationArray);
    return sdRepresentationArray;
  }

  /**
   * Adds compatibility for API level 18.
   */
  @TargetApi(18)
  private static class V18Compat {

    /**
     *
     * Return a pair where the first element is the DRM session manager and the second element is
     * whether the security level is 1.
     *
     * @param player The {@link ExoplayerWrapper} which will receive this renderer builder and use
     *               it to play the video.
     * @param drmCallback Performs {@link android.media.MediaDrm} key and provisioning requests.
     * @throws UnsupportedSchemeException
     */
    public static Pair<DrmSessionManager, Boolean> getDrmSessionManagerData(ExoplayerWrapper player,
        MediaDrmCallback drmCallback) throws UnsupportedSchemeException {
      StreamingDrmSessionManager streamingDrmSessionManager = new StreamingDrmSessionManager(
          ExoplayerUtil.WIDEVINE_UUID,
          player.getPlaybackLooper(),
          drmCallback,
          player.getMainHandler(),
          player);
      return Pair.create((DrmSessionManager) streamingDrmSessionManager,
          getWidevineSecurityLevel(streamingDrmSessionManager) == SECURITY_LEVEL_1);
    }

    /**
     * Reads the security level string from the session manager and returns the appropriate constant
     * - one of {@link #SECURITY_LEVEL_1}, {@link #SECURITY_LEVEL_3},
     * or {@link #SECURITY_LEVEL_UNKNOWN}. The Widevine security levels are documented here
     * https://source.android.com/devices/drm.html#security
     *
     * @param sessionManager Manages the DRM session.
     */
    private static int getWidevineSecurityLevel(StreamingDrmSessionManager sessionManager) {
      String securityLevelProperty = sessionManager.getPropertyString("securityLevel");
      return securityLevelProperty.equals("L1") ? SECURITY_LEVEL_1 : securityLevelProperty
          .equals("L3") ? SECURITY_LEVEL_3 : SECURITY_LEVEL_UNKNOWN;
    }

  }

}