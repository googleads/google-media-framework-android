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

package com.google.googlemediaframeworkdemo.demo.adplayer;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.libraries.mediaframework.exoplayerextensions.ExoplayerWrapper;
import com.google.android.libraries.mediaframework.exoplayerextensions.Video;
import com.google.android.libraries.mediaframework.layeredvideo.Layer;
import com.google.android.libraries.mediaframework.layeredvideo.LayerManager;
import com.google.android.libraries.mediaframework.layeredvideo.PlaybackControlLayer;
import com.google.android.libraries.mediaframework.layeredvideo.SimpleVideoPlayer;
import com.google.android.libraries.mediaframework.layeredvideo.VideoSurfaceLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The ImaPlayer is responsible for displaying both videos and ads. This is accomplished using two
 * video players. The content player displays the user's video. When an ad is requested, the ad
 * video player is overlaid on the content video player. When the ad is complete, the ad video
 * player is destroyed and the content video player is displayed again.
 */
public class ImaPlayer implements AdErrorEvent.AdErrorListener,
    AdsLoader.AdsLoadedListener, AdEvent.AdEventListener {

  private Activity activity;

  /**
   * Contains UI elements for the IMA SDK like the "Learn More" button and the number of seconds
   * remianing in the ad.
   */
  private AdDisplayContainer adDisplayContainer;

  /**
   * Url of the ad.
   */
  private Uri adTagUrl;

  /**
   * Plays the ad.
   */
  private LayerManager adPlayer;

  /**
   * The layout that contains the ad player.
   */
  private FrameLayout adPlayerContainer;

  /**
   * Renders the advertisement.
   */
  private VideoSurfaceLayer adSurfaceLayer;

  /**
   * Used by the IMA SDK to overlay controls (i.e. skip ad) over the ad player.
   */
  private FrameLayout adUiContainer;

  private AdsLoader adsLoader;

  private AdsManager adsManager;

  /**
   * These callbacks are notified when the video is played and when it ends. The IMA SDK uses this
   * to poll for video progress and when to stop the ad.
   */
  private List<VideoAdPlayer.VideoAdPlayerCallback> callbacks;

  /**
   * Contains the content player and the ad frame layout.
   */
  private FrameLayout container;

  /**
   * Plays the content (i.e. the actual video).
   */
  private SimpleVideoPlayer contentPlayer;

  /**
   * Last recorded progress in ad playback. Occasionally the ad pauses when it needs to buffer (and
   * progress stops), so it must be resumed. We detect this situation by noting if the difference
   * "current progress" - "last recorded progress" = 0. If this occurs, then we can pause the
   * video and replay it. This causes the ad to continue playback again.
   */
  private VideoProgressUpdate oldVpu;

  private final ExoplayerWrapper.PlaybackListener playbackListener
      = new ExoplayerWrapper.PlaybackListener() {
    @Override
    public void onError(Exception e) {

    }

    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {
      if (playbackState == ExoPlayer.STATE_ENDED) {
        for (VideoAdPlayer.VideoAdPlayerCallback callback : callbacks) {
          callback.onEnded();
        }
      }
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {

    }
  };

  private final VideoAdPlayer videoAdPlayer = new VideoAdPlayer() {
    @Override
    public void playAd() {
      hideContentPlayer();
      createAdPlayer();
    }

    @Override
    public void loadAd(String mediaUri) {
      adTagUrl = Uri.parse(mediaUri);
    }

    @Override
    public void stopAd() {
      destroyAdPlayer();
      showContentPlayer();
    }

    @Override
    public void pauseAd() {
      if (adPlayer != null){
        adPlayer.getControl().pause();
      }
    }

    @Override
    public void resumeAd() {
      if(adPlayer != null) {
        adPlayer.getControl().start();
      }
    }

    @Override
    public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
      callbacks.add(videoAdPlayerCallback);
    }

    @Override
    public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
      callbacks.remove(videoAdPlayerCallback);
    }

    /**
     * Reports progress in ad player or content player (whichever is currently playing).
     *
     * NOTE: When the ad is buffering, the video is paused. However, when the buffering is
     * complete, the ad is resumed. So, as a workaround, we will attempt to resume the ad, by
     * calling the start method, whenever we detect that the ad is buffering. If the ad is done
     * buffering, the start method will resume playback. If the ad has not finished buffering,
     * then the start method will be ignored.
     */
    @Override
    public VideoProgressUpdate getProgress() {
      VideoProgressUpdate vpu;

      if (adPlayer == null && contentPlayer == null) {
        // If neither player is available, indicate that the time is not ready.
        vpu = VideoProgressUpdate.VIDEO_TIME_NOT_READY;
      } else if (adPlayer != null) {
        // If an ad is playing, report the progress of the ad player.
        vpu = new VideoProgressUpdate(adPlayer.getControl().getCurrentPosition(),
            adPlayer.getControl().getDuration());
      } else {
        // If the cotntent is playing, report the progress of the content player.
        vpu = new VideoProgressUpdate(contentPlayer.getCurrentPosition(),
            contentPlayer.getDuration());
      }


      if (oldVpu == null) {
        oldVpu = vpu;
      } else if ((!vpu.equals(VideoProgressUpdate.VIDEO_TIME_NOT_READY))
          && vpu.getCurrentTime() == oldVpu.getCurrentTime()) {
        // TODO(hsubrama): Find better method for detecting ad pause and resuming ad playback.
        // Resume the ad player if it has paused due to buffering.
        if (adPlayer != null) {
          adPlayer.getControl().pause();
          adPlayer.getControl().start();
        }
      }

      oldVpu = vpu;
      return vpu;
    }
  };

  public ImaPlayer(Activity activity,
                   FrameLayout container,
                   Video video,
                   String videoTitle,
                   ImaSdkSettings sdkSettings,
                   String adTagUrl,
                   PlaybackControlLayer.FullscreenCallback fullscreenCallback) {
    this.activity = activity;
    this.container = container;

    if (adTagUrl != null) {
      this.adTagUrl = Uri.parse(adTagUrl);
    }

    adsLoader = ImaSdkFactory.getInstance().createAdsLoader(activity, sdkSettings);
    adsLoader.addAdErrorListener(this);
    adsLoader.addAdsLoadedListener(this);

    callbacks = new ArrayList<VideoAdPlayer.VideoAdPlayerCallback>();

    boolean autoplay = false;
    contentPlayer = new SimpleVideoPlayer(activity,
        container,
        video,
        videoTitle,
        autoplay,
        fullscreenCallback);

    // Move the content player's surface layer to the background so that the ad player's surface
    // layer can be overlaid on top of it during ad playback.
    contentPlayer.moveSurfaceToBackground();

    // Create the ad adDisplayContainer UI which will be used by the IMA SDK to overlay ad controls.
    adUiContainer = new FrameLayout(activity);
    adUiContainer.setLayoutParams(new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
    container.addView(adUiContainer);
  }

  public ImaPlayer(Activity activity,
                   FrameLayout container,
                   Video video,
                   String videoTitle,
                   ImaSdkSettings sdkSettings,
                   String adTagUrl) {
    this(activity, container, video, videoTitle, sdkSettings, adTagUrl, null);
  }

  public ImaPlayer(Activity activity,
                 FrameLayout container,
                 Video video,
                 String videoTitle,
                 String adTagUrl) {
    this(activity, container, video, videoTitle, createImaSdkSettings(), adTagUrl);
  }

  public ImaPlayer(Activity activity,
                   FrameLayout container,
                   Video video,
                   String videoTitle) {
    this(activity, container, video, videoTitle, createImaSdkSettings(), null);
  }

  public ImaPlayer(Activity activity,
                   FrameLayout container,
                   Video video) {
    this(activity, container, video, "", createImaSdkSettings(), null);
  }

  /**
   * Kill the current ad player, create a new one, and start playing the ad.
   */
  public void createAdPlayer(){
    destroyAdPlayer();

    // Add the ad frame layout to the adDisplayContainer that contains all the content player.
    adPlayerContainer = new FrameLayout(activity);
    adPlayerContainer.setLayoutParams(new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    ));
    container.addView(adPlayerContainer);

    // Ensure tha the ad ui adDisplayContainer is the topmost view.
    container.removeView(adUiContainer);
    container.addView(adUiContainer);

    ArrayList<Layer> adLayer = new ArrayList<Layer>();
    adSurfaceLayer = new VideoSurfaceLayer(false);
    adLayer.add(adSurfaceLayer);
    Video adVideo = new Video(adTagUrl.toString(), Video.VideoType.OTHER);
    adPlayer = new LayerManager(activity, adPlayerContainer, adVideo, adLayer);
    adPlayer.getExoplayerWrapper().addListener(playbackListener);

    // Move the ad player's surface layer to the foreground so that it is overlaid on the content
    // player's surface layer (which is in the background).
    adSurfaceLayer.moveSurfaceToForeground();
    adPlayer.getControl().start();

    // Notify the callbacks that the ad has begun playing.
    for (VideoAdPlayer.VideoAdPlayerCallback callback : callbacks) {
      callback.onPlay();
    }
  }

  public static ImaSdkSettings createImaSdkSettings() {
    return ImaSdkFactory.getInstance().createImaSdkSettings();
  }

  /**
   * Kill the ad player.
   */
  public void destroyAdPlayer(){
    if(adPlayerContainer != null){
      container.removeView(adPlayerContainer);
    }
    if (adUiContainer != null) {
      container.removeView(adUiContainer);
    }
    if(adPlayer != null){
      adPlayer.getExoplayerWrapper().release();
    }
    adPlayerContainer = null;
    adPlayer = null;
  }

  public FrameLayout getUiContainer(){
    return adUiContainer;
  }

  public VideoAdPlayer getVideoAdPlayer() {
    return videoAdPlayer;
  }

  public void hideContentPlayer(){
    contentPlayer.pause();
    contentPlayer.hide();
  }

  @Override
  public void onAdError(AdErrorEvent adErrorEvent) {
    // If there is an error in ad playback, log the error and resume the content.
    Log.d(this.getClass().getSimpleName(), adErrorEvent.getError().getMessage());

    // Display a toast message indicating the error.
    // You should remove this line of code for your production app.
    Toast.makeText(activity, adErrorEvent.getError().getMessage(), Toast.LENGTH_SHORT).show();
    resumeContent();
  }

  @Override
  public void onAdEvent(AdEvent event) {
    switch (event.getType()) {
      case LOADED:
        adsManager.start();
        break;
      case CONTENT_PAUSE_REQUESTED:
        pauseContent();
        break;
      case CONTENT_RESUME_REQUESTED:
        resumeContent();
        break;
      default:
        break;
    }
  }

  @Override
  public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
    adsManager = adsManagerLoadedEvent.getAdsManager();
    adsManager.addAdErrorListener(this);
    adsManager.addAdEventListener(this);
    adsManager.init();
  }

  public void pauseContent(){
    hideContentPlayer();
    for (VideoAdPlayer.VideoAdPlayerCallback callback : callbacks) {
      callback.onPause();
    }
  }

  public void play() {
    if (adTagUrl != null) {
      requestAd();
    } else {
      getContentPlayer().play();
    }
  }

  public void resumeContent(){
    destroyAdPlayer();
    showContentPlayer();
    for (VideoAdPlayer.VideoAdPlayerCallback callback : callbacks) {
      callback.onResume();
    }
  }

  public void setFullscreenCallback(PlaybackControlLayer.FullscreenCallback fullscreenCallback) {
    getContentPlayer().setFullscreenCallback(fullscreenCallback);
  }

  public void showContentPlayer(){
    contentPlayer.show();
    contentPlayer.play();
  }

  public SimpleVideoPlayer getContentPlayer() {
    return contentPlayer;
  }

  public void release() {
    if (adSurfaceLayer != null) {
      adSurfaceLayer.release();
    }
    contentPlayer.release();
  }

  private AdsRequest buildAdsRequest(String tagUrl) {
    adDisplayContainer = ImaSdkFactory.getInstance().createAdDisplayContainer();
    adDisplayContainer.setPlayer(getVideoAdPlayer());
    adDisplayContainer.setAdContainer(getUiContainer());
    AdsRequest request = ImaSdkFactory.getInstance().createAdsRequest();
    request.setAdTagUrl(tagUrl);

    request.setAdDisplayContainer(adDisplayContainer);
    return request;
  }

  private void requestAd() {
    adsLoader.requestAds(buildAdsRequest(adTagUrl.toString()));
  }

}
