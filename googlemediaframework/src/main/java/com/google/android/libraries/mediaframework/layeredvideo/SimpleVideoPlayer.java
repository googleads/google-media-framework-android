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

package com.google.android.libraries.mediaframework.layeredvideo;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.libraries.mediaframework.exoplayerextensions.ExoplayerWrapper;
import com.google.android.libraries.mediaframework.exoplayerextensions.Video;

import java.util.ArrayList;
import java.util.List;

/**
 * A video player which includes subtitle support and a customizable UI for playback control.
 *
 * <p>NOTE: If you want to get a video player up and running with minimal effort, just instantiate
 * this class and call play();
 */
public class SimpleVideoPlayer {

  /**
   * The {@link Activity} that contains this video player.
   */
  private final Activity activity;

  /**
   * The underlying {@link LayerManager} which is used to assemble the player.
   */
  private final LayerManager layerManager;

  /**
   * The customizable UI for playback control (i.e. pause/play, fullscreen, seeking, title, custom
   * actions).
   */
  private final PlaybackControlLayer playbackControlLayer;

  /**
   * Dislayed on bottom center of video player.
   */
  private final SubtitleLayer subtitleLayer;

  /**
   * Renders the video.
   */
  private final VideoSurfaceLayer videoSurfaceLayer;

  public SimpleVideoPlayer(Activity activity,
                           FrameLayout container,
                           Video video,
                           String videoTitle,
                           boolean autoplay) {
    this(activity, container, video, videoTitle, autoplay, null);
  }

  public SimpleVideoPlayer(Activity activity,
                           FrameLayout container,
                           Video video,
                           String videoTitle,
                           boolean autoplay,
                           PlaybackControlLayer.FullscreenCallback fullscreenCallback) {
    this.activity = activity;

    playbackControlLayer = new PlaybackControlLayer(videoTitle, fullscreenCallback);
    subtitleLayer = new SubtitleLayer();
    videoSurfaceLayer = new VideoSurfaceLayer(autoplay);

    List<Layer> layers = new ArrayList<Layer>();
    layers.add(videoSurfaceLayer);
    layers.add(playbackControlLayer);
    layers.add(subtitleLayer);

    layerManager = new LayerManager(activity,
        container,
        video,
        layers);
  }

  /**
   * Creates a button to put in the top right of the video player.
   *
   * @param icon The image of the action (ex. trash can).
   * @param contentDescription The text description this action. This is used in case the
   *                           action buttons do not fit in the video player. If so, an overflow
   *                           button will appear and, when clicked, it will display a list of the
   *                           content descriptions for each action.
   * @param onClickListener The handler for when the action is triggered.
   */
  public void addActionButton(Drawable icon,
                              String contentDescription,
                              View.OnClickListener onClickListener) {
    playbackControlLayer.addActionButton(activity, icon, contentDescription, onClickListener);
  }

  public void addPlaybackListener(ExoplayerWrapper.PlaybackListener listener) {
    layerManager.getExoplayerWrapper().addListener(listener);
  }

  public void disableSeeking() {
    playbackControlLayer.disableSeeking();
  }

  public void enableSeeking() {
    playbackControlLayer.enableSeeking();
  }

  public int getCurrentPosition() {
    return layerManager.getControl().getCurrentPosition();
  }

  public int getDuration() {
    return layerManager.getControl().getDuration();
  }

  public void hide() {
    playbackControlLayer.hide();
    subtitleLayer.setVisibility(View.GONE);
  }

  public void hideTopChrome() {
    playbackControlLayer.hideTopChrome();
  }

  public boolean isFullscreen() {
    return playbackControlLayer.isFullscreen();
  }

  public void setFullscreen(boolean shouldBeFullscreen) {
    playbackControlLayer.setFullscreen(shouldBeFullscreen);
  }

  /**
   * When mutliple surface layers are used (ex. in the case of ad playback), one layer must be
   * overlaid on top of another. This method sends this player's surface layer to the background
   * so that other surface layers can be overlaid on top of it.
   */
  public void moveSurfaceToBackground() {
    videoSurfaceLayer.moveSurfaceToBackground();
  }

  /**
   * When mutliple surface layers are used (ex. in the case of ad playback), one layer must be
   * overlaid on top of another. This method sends this player's surface layer to the foreground
   * so that it is overlaid on top of all layers which are in the background.
   */
  public void moveSurfaceToForeground() {
    videoSurfaceLayer.moveSurfaceToForeground();
  }

  public void pause() {
    // Set the autoplay for the video surface layer in case the surface hasn't been created yet.
    // This way, when the surface is created, it won't start playing.
    videoSurfaceLayer.setAutoplay(false);

    layerManager.getControl().pause();
  }

  public void play() {
    // Set the autoplay for the video surface layer in case the surface hasn't been created yet.
    // This way, when the surface is created, it will automatically start playing.
    videoSurfaceLayer.setAutoplay(true);

    layerManager.getControl().start();
  }

  public void setChromeColor(int color) {
    playbackControlLayer.setChromeColor(color);
  }

  public void setFullscreenCallback(PlaybackControlLayer.FullscreenCallback fullscreenCallback) {
    playbackControlLayer.setFullscreenCallback(fullscreenCallback);
  }

  public void setLogoImage(Drawable logo) {
    playbackControlLayer.setLogoImageView(logo);
  }

  public void setPlaybackControlColor(int color) {
    playbackControlLayer.setControlColor(color);
  }

  public void setSeekbarColor(int color) {
    playbackControlLayer.setSeekbarColor(color);
  }

  public void setTextColor(int color) {
    playbackControlLayer.setTextColor(color);
  }

  public void setVideoTitle (String title) {
    playbackControlLayer.setVideoTitle(title);
  }

  public boolean shouldBePlaying() {
    return playbackControlLayer.shouldBePlaying();
  }

  public void show() {
    playbackControlLayer.show();
    subtitleLayer.setVisibility(View.VISIBLE);
  }

  public void showTopChrome() {
    playbackControlLayer.showTopChrome();
  }

  public void release() {
    videoSurfaceLayer.release();
    layerManager.release();
  }

}
