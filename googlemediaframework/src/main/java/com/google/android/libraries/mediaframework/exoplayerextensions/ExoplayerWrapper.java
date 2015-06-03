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

import android.media.MediaCodec.CryptoException;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer.DecoderInitializationException;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.chunk.ChunkSampleSource;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.chunk.MultiTrackChunkSource;
import com.google.android.exoplayer.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer;
import com.google.android.exoplayer.text.TextRenderer;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A wrapper around {@link ExoPlayer} that provides a higher level interface. It can be prepared
 * with one of a number of {@link RendererBuilder} classes to suit different use cases (e.g. DASH,
 * SmoothStreaming and so on).
 */
public class ExoplayerWrapper implements ExoPlayer.Listener, ChunkSampleSource.EventListener,
        HlsSampleSource.EventListener, DefaultBandwidthMeter.EventListener,
        MediaCodecVideoTrackRenderer.EventListener,
    MediaCodecAudioTrackRenderer.EventListener, TextRenderer,
    StreamingDrmSessionManager.EventListener {

  /**
   * Builds renderers for the player.
   */
  public interface RendererBuilder {
    /**
     * Constructs the necessary components for playback.
     *
     * @param player The parent player.
     * @param callback The callback to invoke with the constructed components.
     */
    void buildRenderers(ExoplayerWrapper player, RendererBuilderCallback callback);
  }

  /**
   * A callback invoked by a {@link RendererBuilder}.
   */
  public interface RendererBuilderCallback {
    /**
     * Invoked with the results from a {@link RendererBuilder}.
     *
     * @param trackNames The names of the available tracks, indexed by {@link ExoplayerWrapper} TYPE_*
     *     constants. May be null if the track names are unknown. An individual element may be null
     *     if the track names are unknown for the corresponding type.
     * @param multiTrackSources Sources capable of switching between multiple available tracks,
     *     indexed by {@link ExoplayerWrapper} TYPE_* constants. May be null if there are no types with
     *     multiple tracks. An individual element may be null if it does not have multiple tracks.
     * @param renderers Renderers indexed by {@link ExoplayerWrapper} TYPE_* constants. An individual
     *     element may be null if there do not exist tracks of the corresponding type.
     */
    void onRenderers(String[][] trackNames, MultiTrackChunkSource[] multiTrackSources,
                          TrackRenderer[] renderers);
    /**
     * Invoked if a {@link RendererBuilder} encounters an error.
     *
     * @param e Describes the error.
     */
    void onRenderersError(Exception e);
  }

  /**
   * A listener for core events.
   */
  public interface Listener {
    void onStateChanged(boolean playWhenReady, int playbackState);
    void onError(Exception e);
    void onVideoSizeChanged(int width, int height, float pixelWidthHeightRatio);
  }

  /**
   * A listener for internal errors.
   * <p>
   * These errors are not visible to the user, and hence this listener is provided for
   * informational purposes only. Note however that an internal error may cause a fatal
   * error if the player fails to recover. If this happens, {@link Listener#onError(Exception)}
   * will be invoked.
   */
  public interface InternalErrorListener {

    /**
     * Respond to error in renderer initialization.
     * @param e The error.
     */
    void onRendererInitializationError(Exception e);

    /**
     * Respond to error in initializing the audio track.
     * @param e The error.
     */
    void onAudioTrackInitializationError(AudioTrack.InitializationException e);

    /**
     * Respond to error when writing the audio track.
     * @param e The error.
     */
    void onAudioTrackWriteError(AudioTrack.WriteException e);

    /**
     * Respond to error in initializing the decoder.
     * @param e The error.
     */
    void onDecoderInitializationError(DecoderInitializationException e);

    /**
     * Respond to error in setting up security of video.
     * @param e The error.
     */
    void onCryptoError(CryptoException e);
    void onLoadError(int sourceId, IOException e);
    void onDrmSessionManagerError(Exception e);
  }

  /**
   * A listener for debugging information.
   */
  public interface InfoListener {
    void onVideoFormatEnabled(Format format, int trigger, int mediaTimeMs);
    void onAudioFormatEnabled(Format format, int trigger, int mediaTimeMs);
    void onDroppedFrames(int count, long elapsed);
    void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate);
    void onLoadStarted(int sourceId, long length, int type, int trigger, Format format,
                       int mediaStartTimeMs, int mediaEndTimeMs);
    void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format,
                         int mediaStartTimeMs, int mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs);
    void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
                              long initializationDurationMs);
  }

  /**
   * A listener for receiving notifications of timed text.
   */
  public interface TextListener {
    void onText(String text);
  }

  /**
   * A listener for receiving ID3 metadata parsed from the media stream.
   */
  public interface Id3MetadataListener {
    void onId3Metadata(Map<String, Object> metadata);
  }

  // Constants pulled into this class for convenience.
  public static final int STATE_IDLE = ExoPlayer.STATE_IDLE;
  public static final int STATE_PREPARING = ExoPlayer.STATE_PREPARING;
  public static final int STATE_BUFFERING = ExoPlayer.STATE_BUFFERING;
  public static final int STATE_READY = ExoPlayer.STATE_READY;
  public static final int STATE_ENDED = ExoPlayer.STATE_ENDED;

  public static final int RENDERER_COUNT = 5;

  /**
   * The element at index TYPE_VIDEO is a video type renderer.
   */
  public static final int TYPE_VIDEO = 0;

  /**
   * The element at index TYPE_AUDIO is an audio type renderer.
   */
  public static final int TYPE_AUDIO = 1;

  /**
   * The element at index TYPE_TEXT is a text type renderer.
   */
  public static final int TYPE_TEXT = 2;

  /**
   * For id3 metadata.
   */
  public static final int TYPE_TIMED_METADATA = 3;

  /**
   * The element at index TYPE_DEBUG is a debug type renderer.
   */
  public static final int TYPE_DEBUG = 4;

  /**
   * This variable must be an int, not part of an enum because it has significance within the
   * Exoplayer library.
   */
  private static final int RENDERER_BUILDING_STATE_IDLE = 1;

  /**
   * This variable must be an int, not part of an enum because it has significance within the
   * Exoplayer library.
   */
  private static final int RENDERER_BUILDING_STATE_BUILDING = 2;
  private static final int RENDERER_BUILDING_STATE_BUILT = 3;

  /**
   * This variable must be an int, not part of an enum because it has significance within the
   * Exoplayer library.
   */
  public static final int DISABLED_TRACK = -1;

  /**
   * This variable must be an int, not part of an enum because it has significance within the
   * Exoplayer library.
   */
  public static final int PRIMARY_TRACK = 0;

  /**
   * Responsible for loading the data from the source, processing it, and providing byte streams.
   * By modifying the renderer builder, we can support different video formats like DASH, MP4, and
   * SmoothStreaming.
   */
  private final RendererBuilder rendererBuilder;

  /**
   * The underlying Exoplayer instance responsible for playing the video.
   */
  private final ExoPlayer player;
  private final ObservablePlayerControl playerControl;
  private final Handler mainHandler;

  /**
   * Listeners are notified when the video size changes, when the underlying player's state changes,
   * or when an error occurs.
   */
  private final CopyOnWriteArrayList<Listener> listeners;

  /**
   * States are idle, building, or built.
   */
  private int rendererBuildingState;

  /**
   * States are idle, prepared, buffering, ready, or ended. This is an integer (instead of an enum)
   * because the Exoplayer library uses integers.
   */
  private int lastReportedPlaybackState;

  /**
   * Whether the player was in a playWhenReady state the last time we checked.
   */
  private boolean lastReportedPlayWhenReady;

  /**
   * The surface on which the video is rendered.
   */
  private Surface surface;

  /**
   * Responds to successful render build, error, or cancellation.
   */
  private InternalRendererBuilderCallback builderCallback;

  /**
   * Renders the video data.
   */
  private TrackRenderer videoRenderer;

  private Format videoFormat;

  private int videoTrackToRestore;


  /**
   * Sources capable of switching between multiple available tracks,
   * indexed by ExoplayerWrapper INDEX_* constants. May be null if there are no types with
   * multiple tracks. An individual element may be null if it does not have multiple tracks.
   */
  private MultiTrackChunkSource[] multiTrackSources;

  /**
   * The names of the available tracks, indexed by ExoplayerWrapper INDEX_* constants.
   * May be null if the track names are unknown. An individual element may be null if the track
   * names are unknown for the corresponding type.
   */
  private String[][] trackNames;

  /**
   * A list of enabled or disabled tracks to render.
   */
  private int[] selectedTracks;
  private boolean backgrounded;

  /**
   * The state of a track at a given index (one of the TYPE_* constants).
   */
//  private int[] trackStateForType;

  /**
   * Respond to text (ex subtitle or closed captioning) events.
   */
  private TextListener textListener;

  private Id3MetadataListener id3MetadataListener;

  /**
   * Respond to errors that occur in Exoplayer.
   */
  private InternalErrorListener internalErrorListener;

  /**
   * Respond to changes in media format changes, load events, bandwidth estimates,
   * and dropped frames.
   */
  private InfoListener infoListener;

  /**
   * @param rendererBuilder Responsible for loading the data from the source, processing it,
   *                        and providing byte streams. By modifying the renderer builder, we can
   *                        support different video formats like DASH, MP4, and SmoothStreaming.
   */
  public ExoplayerWrapper(RendererBuilder rendererBuilder) {
    this.rendererBuilder = rendererBuilder;
    player = ExoPlayer.Factory.newInstance(RENDERER_COUNT, 1000, 5000);
    player.addListener(this);
    playerControl = new ObservablePlayerControl(player);
    mainHandler = new Handler();
    listeners = new CopyOnWriteArrayList<Listener>();
    lastReportedPlaybackState = STATE_IDLE;
    rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
    selectedTracks = new int[RENDERER_COUNT];
    // Disable text initially.
    selectedTracks[TYPE_TEXT] = DISABLED_TRACK;
  }

  public ObservablePlayerControl getPlayerControl() {
    return playerControl;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  /**
   * Set a listener to respond to errors within Exoplayer.
   * @param listener The listener which responds to the error events.
   */
  public void setInternalErrorListener(InternalErrorListener listener) {
    internalErrorListener = listener;
  }

  /**
   * Set a listener to respond to media format changes, bandwidth samples, load events, and dropped
   * frames.
   * @param listener Listens to media format changes, bandwidth samples, load events, and dropped
   *                 frames.
   */
  public void setInfoListener(InfoListener listener) {
    infoListener = listener;
  }

  /**
   * Set the listener which responds to incoming text (ex subtitles or captions).
   *
   * @param listener The listener which can respond to text like subtitles and captions.
   */
  public void setTextListener(TextListener listener) {
    textListener = listener;
  }

  public void setMetadataListener(Id3MetadataListener listener) {
    id3MetadataListener = listener;
  }

  public void setSurface(Surface surface) {
    this.surface = surface;
    pushSurface(false);
  }

  /**
   * Returns the surface on which the video is rendered.
   */
  public Surface getSurface() {
    return surface;
  }

  /**
   * Clear the video surface.
   *
   * <p>In order to clear the surface, a message must be sent to the playback thread. To guarantee
   * that this message is delivered, Exoplayer uses a blocking operation. Therefore, this method is
   * blocking.
   */
  public void blockingClearSurface() {
    surface = null;
    pushSurface(true);
  }

  /**
   * Returns the name of the track at the given index.
   * @param type The index indicating the type of video (ex {@link #TYPE_VIDEO})
   */
  public String[] getTracks(int type) {
    return trackNames == null ? null : trackNames[type];
  }

  public int getSelectedTrackIndex(int type) {
    return selectedTracks[type];
  }

  public void selectTrack(int type, int index) {
    if (selectedTracks[type] == index) {
      return;
    }
    selectedTracks[type] = index;
      pushTrackSelection(type, true);
    if (type == TYPE_TEXT && index == DISABLED_TRACK && textListener != null) {
      textListener.onText(null);
    }
  }

  public Format getVideoFormat() {
    return videoFormat;
  }

  public void setBackgrounded(boolean backgrounded) {
    if (this.backgrounded == backgrounded) {
      return;
    }
    this.backgrounded = backgrounded;
    if (backgrounded) {
      videoTrackToRestore = getSelectedTrackIndex(TYPE_VIDEO);
      selectTrack(TYPE_VIDEO, DISABLED_TRACK);
      blockingClearSurface();
    } else {
      selectTrack(TYPE_VIDEO, videoTrackToRestore);
    }
  }

  public void prepare() {
    if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILT) {
      player.stop();
    }
    if (builderCallback != null) {
      builderCallback.cancel();
    }
    videoFormat = null;
    videoRenderer = null;
    multiTrackSources = null;
    rendererBuildingState = RENDERER_BUILDING_STATE_BUILDING;
    maybeReportPlayerState();
    builderCallback = new InternalRendererBuilderCallback();
    rendererBuilder.buildRenderers(this, builderCallback);
  }

  /**
   * Invoked with the results from a {@link RendererBuilder}.
   *
   * @param trackNames The names of the available tracks, indexed by {@link ExoplayerWrapper}
   *                   TYPE_* constants. May be null if the track names are unknown. An individual
   *                   element may be null if the track names are unknown for the corresponding
   *                   type.
   * @param multiTrackSources Sources capable of switching between multiple available tracks,
   *                          indexed by {@link ExoplayerWrapper} TYPE_* constants. May be null
   *                          if there are no types with multiple tracks. An individual element
   *                          may be null if it does not have multiple tracks.
   * @param renderers Renderers indexed by {@link ExoplayerWrapper} TYPE_* constants. An
   *                  individual element may be null if there do not exist tracks of the
   *                  corresponding type.
   */
  /* package */ void onRenderers(String[][] trackNames,
      MultiTrackChunkSource[] multiTrackSources, TrackRenderer[] renderers) {
    builderCallback = null;
    // Normalize the results.
    if (trackNames == null) {
      trackNames = new String[RENDERER_COUNT][];
    }
    if (multiTrackSources == null) {
      multiTrackSources = new MultiTrackChunkSource[RENDERER_COUNT];
    }
    for (int i = 0; i < RENDERER_COUNT; i++) {
      if (renderers[i] == null) {
        // Convert a null renderer to a dummy renderer.
        renderers[i] = new DummyTrackRenderer();
      } else if (trackNames[i] == null) {
        // We have a renderer so we must have at least one track, but the names are unknown.
        // Initialize the correct number of null track names.
        int trackCount = multiTrackSources[i] == null ? 1 : multiTrackSources[i].getTrackCount();
        trackNames[i] = new String[trackCount];
      }
    }
    // Complete preparation.
    this.videoRenderer = renderers[TYPE_VIDEO];
    this.trackNames = trackNames;
    this.multiTrackSources = multiTrackSources;
    pushSurface(false);
    pushTrackSelection(TYPE_VIDEO, true);
    pushTrackSelection(TYPE_AUDIO, true);
    pushTrackSelection(TYPE_TEXT, true);
    player.prepare(renderers);
    rendererBuildingState = RENDERER_BUILDING_STATE_BUILT;
  }

  /**
   * Notify the listeners when an exception is thrown.
   * @param e The exception that has been thrown.
   */
  /* package */ void onRenderersError(Exception e) {
    builderCallback = null;
    if (internalErrorListener != null) {
      internalErrorListener.onRendererInitializationError(e);
    }
    for (Listener listener : listeners) {
      listener.onError(e);
    }
    rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
    maybeReportPlayerState();
  }

  /**
   * Set whether the player should begin as soon as it is setup.
   * @param playWhenReady If true, playback will start as soon as the player is setup. If false, it
   *                      must be started programmatically.
   */
  public void setPlayWhenReady(boolean playWhenReady) {
    player.setPlayWhenReady(playWhenReady);
  }

  /**
   * Move the seek head to the given position.
   * @param positionMs A number of milliseconds after the start of the video.
   */
  public void seekTo(long positionMs) {
    player.seekTo(positionMs);
  }

  /**
   * When you are finished using this object, make sure to call this method.
   */
  public void release() {
    if (builderCallback != null) {
      builderCallback.cancel();
      builderCallback = null;
    }
    rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
    surface = null;
    player.release();
  }

  /**
   * Returns the state of the Exoplayer instance.
   */
  public int getPlaybackState() {
    if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILDING) {
      return ExoPlayer.STATE_PREPARING;
    }
    int playerState = player.getPlaybackState();
    if (rendererBuildingState == RENDERER_BUILDING_STATE_BUILT
        && rendererBuildingState == RENDERER_BUILDING_STATE_IDLE) {
      // This is an edge case where the renderers are built, but are still being passed to the
      // player's playback thread.
      return ExoPlayer.STATE_PREPARING;
    }
    return playerState;
  }

  /**
   * Returns the position of the seek head in the number of
   * milliseconds after the start of the video.
   */
  public long getCurrentPosition() {
    return player.getCurrentPosition();
  }

  /**
   * Returns the duration of the video in milliseconds.
   */
  public long getDuration() {
    return player.getDuration();
  }

  /**
   * Returns the number of the milliseconds of the video that has been buffered.
   */
  public int getBufferedPercentage() {
    return player.getBufferedPercentage();
  }

  /**
   * Returns true if the video is set to start as soon as it is set up, returns false otherwise.
   */
  public boolean getPlayWhenReady() {
    return player.getPlayWhenReady();
  }

  /**
   * Return the looper of the Exoplayer instance which sits and waits for messages.
   */
  /* package */ Looper getPlaybackLooper() {
    return player.getPlaybackLooper();
  }

  /**
   * Returns the handler which responds to messages.
   */
  /* package */ Handler getMainHandler() {
    return mainHandler;
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int state) {
    maybeReportPlayerState();
  }

  @Override
  public void onPlayerError(ExoPlaybackException exception) {
    rendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
    for (Listener listener : listeners) {
      listener.onError(exception);
    }
  }

  @Override
  public void onVideoSizeChanged(int width, int height, float pixelWidthHeightRatio) {
    for (Listener listener : listeners) {
      listener.onVideoSizeChanged(width, height, pixelWidthHeightRatio);
    }
  }

  @Override
  public void onDroppedFrames(int count, long elapsed) {
    if (infoListener != null) {
      infoListener.onDroppedFrames(count, elapsed);
    }
  }

  @Override
  public void onBandwidthSample(int elapsedMs, long bytes, long bandwidthEstimate) {
    if (infoListener != null) {
      infoListener.onBandwidthSample(elapsedMs, bytes, bandwidthEstimate);
    }
  }

  @Override
  public void onDrmSessionManagerError(Exception e) {
    if (internalErrorListener != null) {
      internalErrorListener.onDrmSessionManagerError(e);
    }
  }

  @Override
  public void onDecoderInitializationError(DecoderInitializationException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onDecoderInitializationError(e);
    }
  }

  @Override
  public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onAudioTrackInitializationError(e);
    }
  }

  @Override
  public void onAudioTrackWriteError(AudioTrack.WriteException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onAudioTrackWriteError(e);
    }
  }

  @Override
  public void onCryptoError(CryptoException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onCryptoError(e);
    }
  }

  @Override
  public void onDecoderInitialized(
      String decoderName,
      long elapsedRealtimeMs,
      long initializationDurationMs) {
    if (infoListener != null) {
      infoListener.onDecoderInitialized(decoderName, elapsedRealtimeMs, initializationDurationMs);
    }
  }

  @Override
  public void onLoadError(int sourceId, IOException e) {
    if (internalErrorListener != null) {
      internalErrorListener.onLoadError(sourceId, e);
    }
  }

  @Override
  public void onText(String text) {
    processText(text);
  }

  /* package */ MetadataTrackRenderer.MetadataRenderer<Map<String, Object>>
      getId3MetadataRenderer() {
    return new MetadataTrackRenderer.MetadataRenderer<Map<String, Object>>() {
      @Override
      public void onMetadata(Map<String, Object> metadata) {
        if (id3MetadataListener != null) {
          id3MetadataListener.onId3Metadata(metadata);
        }
      }
    };
  }

  @Override
  public void onPlayWhenReadyCommitted() {
    // Do nothing.
  }

  @Override
  public void onDrawnToSurface(Surface surface) {
    // Do nothing.
  }

  @Override
  public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format,
      int mediaStartTimeMs, int mediaEndTimeMs) {
    if (infoListener != null) {
      infoListener.onLoadStarted(sourceId, length, type, trigger, format, mediaStartTimeMs,
          mediaEndTimeMs);
    }
  }

  @Override
  public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format,
      int mediaStartTimeMs, int mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {
    if (infoListener != null) {
      infoListener.onLoadCompleted(sourceId, bytesLoaded, type, trigger, format, mediaStartTimeMs,
          mediaEndTimeMs, elapsedRealtimeMs, loadDurationMs);
    }
  }

  @Override
  public void onLoadCanceled(int sourceId, long bytesLoaded) {
    // Do nothing.
  }

  @Override
  public void onUpstreamDiscarded(int sourceId, int mediaStartTimeMs, int mediaEndTimeMs) {
    // Do nothing.
  }

  @Override
  public void onDownstreamFormatChanged(int sourceId, Format format, int trigger, int mediaTimeMs) {
    if (infoListener == null) {
      return;
    }
    if (sourceId == TYPE_VIDEO) {
      videoFormat = format;
      infoListener.onVideoFormatEnabled(format, trigger, mediaTimeMs);
    } else if (sourceId == TYPE_AUDIO) {
      infoListener.onAudioFormatEnabled(format, trigger, mediaTimeMs);
    }
  }

  /**
   * If either playback state or the play when ready values have changed, notify all the playback
   * listeners.
   */
  private void maybeReportPlayerState() {
    boolean playWhenReady = player.getPlayWhenReady();
    int playbackState = getPlaybackState();
    if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
      for (Listener listener : listeners) {
        listener.onStateChanged(playWhenReady, playbackState);
      }
      lastReportedPlayWhenReady = playWhenReady;
      lastReportedPlaybackState = playbackState;
    }
  }

  /**
   * Updated the playback thread with the latest video renderer and surface.
   * @param blockForSurfacePush If true, then message sent to the underlying playback thread is
   *                            guaranteed to be delivered. However, this is a blocking operation
   */
  private void pushSurface(boolean blockForSurfacePush) {
    if (videoRenderer == null) {
      return;
    }

    if (blockForSurfacePush) {
      player.blockingSendMessage(
              videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
    } else {
      player.sendMessage(
              videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
    }
  }


  /**
   * Send the renderer at trackIndex to the underlying player.
   * @param type The index of the video type (it must be one of the TYPE_* constants).
   * @param allowRendererEnable If true, the renderer is enabled.
   */
  private void pushTrackSelection(int type, boolean allowRendererEnable) {
    if (multiTrackSources == null) {
      return;
    }

    int trackIndex = selectedTracks[type];
    if (trackIndex == DISABLED_TRACK) {
      player.setRendererEnabled(type, false);
    } else if (multiTrackSources[type] == null) {
      player.setRendererEnabled(type, allowRendererEnable);
    } else {
      boolean playWhenReady = player.getPlayWhenReady();
      player.setPlayWhenReady(false);
      player.setRendererEnabled(type, false);
      player.sendMessage(multiTrackSources[type], MultiTrackChunkSource.MSG_SELECT_TRACK,
          trackIndex);
      player.setRendererEnabled(type, allowRendererEnable);
      player.setPlayWhenReady(playWhenReady);
    }
  }

  /* package */ void processText(String text) {
    if (textListener == null || selectedTracks[TYPE_TEXT] == DISABLED_TRACK) {
      return;
    }
    textListener.onText(text);
  }

  /**
   * Responds to a successful renderer build or an error.
   */
  private class InternalRendererBuilderCallback implements RendererBuilderCallback {

    private boolean canceled;

    public void cancel() {
      canceled = true;
    }

    @Override
    public void onRenderers(String[][] trackNames, MultiTrackChunkSource[] multiTrackSources,
                                 TrackRenderer[] renderers) {
      if (!canceled) {
        ExoplayerWrapper.this.onRenderers(trackNames, multiTrackSources, renderers);
      }
    }

    @Override
    public void onRenderersError(Exception e) {
      if (!canceled) {
        ExoplayerWrapper.this.onRenderersError(e);
      }
    }

  }

  }
