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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.libraries.mediaframework.R;
import com.google.android.libraries.mediaframework.exoplayerextensions.PlayerControlCallback;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;


/**
 * A {@link Layer} that creates a customizable user interface for controlling video playback.
 *
 * <p>The UI consists of:
 * 1) a top bar which contains a logo, title, and set of action buttons.
 * 2) a bottom bar which contains a seek bar, fullscreen button, and text views indicating
 * the current time and total duration of the video.
 * 3) a translucent middle section which displays a pause/play button.
 *
 * <p>The UI appears when the container containing the {@link PlaybackControlLayer} is tapped. It
 * automatically disappears after a given time.
 *
 * <p>The UI can be customized by:
 * 1) Setting the color of the top bar, bottom bar, and middle section - this is called the chrome
 * tint color.
 * 2) Setting the color of the text - this is called the text color.
 * 3) Setting the color of the buttons and seek bar - this is called the control tint color.
 * 4) Setting the logo image displayed in the left of the top bar.
 * 5) Setting the title of the video displayed in the left of the top bar
 * (and to the right of the logo).
 * 6) Adding an action button by providing an image, a content description, and a click handler. If
 * there is enough room, the action buttons will be displayed on the right of the top chrome. If
 * there is NOT enough room, an overflow button will be displayed. When the overflow button is
 * clicked, a dialog box listing the content descriptions for the action buttons is displayed. By
 * clicking an action in the list, the action can be triggered.
 *
 * <p>The view is defined in the layout file: res/layout/playback_control_layer.xml.
 */
public class PlaybackControlLayer implements Layer, PlayerControlCallback {

  public interface FullscreenCallback {

    /**
     * When triggered, the activity should hide any additional views.
     */
    public void onGoToFullscreen();

    /**
     * When triggered, the activity should show any views that were hidden when the player
     * went to fullscreen.
     */
    public void onReturnFromFullscreen();
  }

  /**
   * Message handler which allows us to send delayed messages to the {@link PlaybackControlLayer}
   * This is useful for fading out the view after a certain time.
   */
  private static class MessageHandler extends Handler {
    private final WeakReference<PlaybackControlLayer> playerView;

    private MessageHandler(PlaybackControlLayer playbackControlLayer) {
      playerView = new WeakReference<PlaybackControlLayer>(playbackControlLayer);
    }

    @Override
    public void handleMessage(Message msg) {
      PlaybackControlLayer layer = playerView.get();
      if (layer == null || layer.getLayerManager().getControl() == null) {
        return;
      }
      int pos;
      switch (msg.what) {
        case FADE_OUT:
          layer.hide();
          break;
        case SHOW_PROGRESS:
          pos = layer.updateProgress();
          if (!layer.isSeekbarDragging
              && layer.areControlsVisible
              && layer.getLayerManager().getControl().isPlaying()) {
            msg = obtainMessage(SHOW_PROGRESS);
            sendMessageDelayed(msg, 1000 - (pos % 1000));
          }
          break;
      }
    }
  }

  /**
   * The chrome (i.e. the top bar, bottom bar, and middle section) is by default a slightly
   * transparent black.
   */
  public static final int DEFAULT_CHROME_COLOR = Color.argb(140, 0, 0, 0);

  /**
   * By default, there is no tint to the controls.
   */
  public static final int DEFAULT_CONTROL_TINT_COLOR = Color.TRANSPARENT;

  public static final int DEFAULT_TEXT_COLOR = Color.WHITE;

  /**
   * When the playback controls are shown, hide them after DEFAULT_TIMEOUT_MS milliseconds.
   */
  private static final int DEFAULT_TIMEOUT_MS = 3000;

  /**
   * Used by the {@link MessageHandler} to indicate that media controls should fade out.
   */
  private static final int FADE_OUT = 1;

  /**
   * Used by the {@link MessageHandler} to indicate that media controls should update progress bar.
   */
  private static final int SHOW_PROGRESS = 2;

  /**
   * List of image buttons which are displayed in the right side of the top chrome.
   */
  private List<ImageButton> actionButtons;

  private boolean areControlsVisible;

  /**
   * Contains the seek bar, current time, end time, and fullscreen button. The background can
   * be tinted with a color for branding.
   */
  private LinearLayout bottomChrome;

  /**
   * Whether the user can drag the seek bar thumb to seek.
   */
  private boolean canSeek;

  /**
   * Derived from the Color class. The chrome consists of three UI elements:
   * 1) The top bar which contains the logo, title, and action buttons.
   * 2) The bottom bar which contains the play/pause button, seekBar, and fullscreen buttons.
   * 3) The translucent middle section of the PlaybackControlLayer.
   * The chromeColor changes the color of each of these elements.
   */
  private int chromeColor;

  /**
   * Derived from the {@link Color} class (ex. {@link Color#RED}), this is the color of the
   * play/pause button, fullscreen button, seek bar, and action buttons.
   */
  private int controlColor;

  /**
   * Elapsed time into video.
   */
  private TextView currentTime;

  /**
   * Duration of the video.
   */
  private TextView endTime;

  /**
   * Makes player fullscreen. This button is not displayed unless there is a
   * {@link FullscreenCallback} associated with this object.
   */
  private ImageButton fullscreenButton;

  /**
   * This callback is triggered when going to fullscreen and returning from fullscreen.
   */
  private FullscreenCallback fullscreenCallback;

  /**
   * The message handler which deals with displaying progress and fading out the media controls
   * We use it so that we can make the view fade out after a timeout (by sending a delayed message).
   */
  private Handler handler = new MessageHandler(this);

  private boolean isFullscreen;

  private boolean isSeekbarDragging;

  private LayerManager layerManager;

  /**
   * The drawable that will be displayed in the {@link PlaybackControlLayer#logoImageView}.
   */
  private Drawable logoDrawable;

  /**
   * Displayed in the left of the top bar - shows a logo. This is optional; if no image is provided,
   * then no logo will be displayed.
   */
  private ImageView logoImageView;


  /**
   * These is the layout of the container before fullscreen mode has been entered.
   * When we leave fullscreen mode, we restore the layout of the container to this layout.
   */
  private ViewGroup.LayoutParams originalContainerLayoutParams;

  /**
   * Contains the actions buttons (displayed in right of the top bar).
   */
  private LinearLayout actionButtonsContainer;

  private ImageButton pausePlayButton;

  private SeekBar seekBar;

  /**
   * Derived from the {@link Color} class (ex. {@link Color#RED}).
   */
  private int seekbarColor;

  /**
   * Whether the play button has been pressed and the video should be playing.
   * We include this variable because the video may pause when buffering must occur. Although
   * the video will usually resume automatically when the buffering is complete, there are instances
   * (i.e. ad playback), where it will not resume automatically. So, if we detect that the video is
   * paused after buffering and should be playing, we can resume it programmatically.
   */
  private boolean shouldBePlaying;

  /**
   * Derived from the {@link Color} class (ex. {@link Color#RED}).
   */
  private int textColor;

  /**
   * Formats times to HH:MM:SS or MM:SS form.
   */
  private StringBuilder timeFormatBuilder;

  /**
   * Formats times to HH:MM:SS or MM:SS form.
   */
  private Formatter timeFormatter;

  private FrameLayout middleSection;

  /**
   * Contains the logo, video title, and other actions button. It can be tinted with a color for
   * branding.
   */
  private RelativeLayout topChrome;

  /**
   * The title displayed in the {@link PlaybackControlLayer#videoTitleView}.
   */
  private String videoTitle;

  /**
   * Video title displayed in the left of the top chrome.
   */
  private TextView videoTitleView;

  /**
   * The view created by this {@link PlaybackControlLayer}
   */
  private FrameLayout view;

  public PlaybackControlLayer(String videoTitle) {
    this(videoTitle, null);
  }

  public PlaybackControlLayer(String videoTitle, FullscreenCallback fullscreenCallback) {
    this.videoTitle = videoTitle;
    this.canSeek = true;
    this.fullscreenCallback = fullscreenCallback;
    this.shouldBePlaying = false;
    actionButtons = new ArrayList<ImageButton>();
  }

  /**
   * Creates a button to put in the set of action buttons at the right of the top bar.
   * @param activity The activity that contains the video player.
   * @param icon The image of the action (ex. trash can).
   * @param contentDescription The text description this action. This is used in case the
   *                           action buttons do not fit in the video player. If so, an overflow
   *                           button will appear and, when clicked, it will display a list of the
   *                           content descriptions for each action.
   * @param onClickListener The handler for when the action is triggered.
   */
  public void addActionButton(Activity activity,
                              Drawable icon,
                              String contentDescription,
                              View.OnClickListener onClickListener) {
    ImageButton button = new ImageButton(activity);

    button.setContentDescription(contentDescription);
    button.setImageDrawable(icon);
    button.setOnClickListener(onClickListener);

    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    );

    int margin = activity.getResources().getDisplayMetrics().densityDpi * 5;
    layoutParams.setMargins(margin, 0, margin, 0);

    button.setBackgroundColor(Color.TRANSPARENT);
    button.setLayoutParams(layoutParams);

    isFullscreen = false;

    actionButtons.add(button);

    if (middleSection != null) {
      updateActionButtons();
      updateColors();
    }
  }

  @Override
  public FrameLayout createView(LayerManager layerManager) {
    this.layerManager = layerManager;

    LayoutInflater inflater = layerManager.getActivity().getLayoutInflater();

    view = (FrameLayout) inflater.inflate(R.layout.playback_control_layer, null);
    setupView();

    originalContainerLayoutParams = layerManager
        .getContainer()
        .getLayoutParams();

    layerManager.getControl().addCallback(this);

    textColor = DEFAULT_TEXT_COLOR;
    chromeColor = DEFAULT_CHROME_COLOR;
    controlColor = DEFAULT_CONTROL_TINT_COLOR;
    seekbarColor = DEFAULT_CONTROL_TINT_COLOR;

    if (logoDrawable != null) {
      logoImageView.setImageDrawable(logoDrawable);
    }

    getLayerManager().getContainer().setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (areControlsVisible) {
          hide();
        } else {
          show();
        }
      }
    });

    return view;
  }

  public void disableSeeking() {
    this.canSeek = false;
    if (middleSection != null) {
      updateColors();
    }
  }

  /**
   * Fullscreen mode will rotate to landscape mode, hide the action bar, and make the video player
   * take up the full size of the display. The developer who is using this function must ensure the
   * following:
   *
   * <p>1) Inside the android manifest, the activity that uses the video player has the attribute
   * android:configChanges="orientation".
   *
   * <p>2) Other views in the activity (or fragment) are
   * hidden (or made visible) when this method is called.
   */
  public void doToggleFullscreen() {

    // If there is no callback for handling fullscreen, don't do anything.
    if (fullscreenCallback == null) {
      return;
    }
    PlayerControl playerControl = getLayerManager().getControl();
    if (playerControl == null) {
      return;
    }

    Activity activity = getLayerManager().getActivity();
    FrameLayout container = getLayerManager().getContainer();

    if (isFullscreen) {
      fullscreenCallback.onReturnFromFullscreen();
      activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

      // Make the status bar and navigation bar visible again.
      activity.getWindow().getDecorView().setSystemUiVisibility(0);

      container.setLayoutParams(originalContainerLayoutParams);

      fullscreenButton.setImageResource(R.drawable.ic_action_full_screen);

      isFullscreen = false;
    } else {
      fullscreenCallback.onGoToFullscreen();
      activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

      activity.getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
          View.SYSTEM_UI_FLAG_FULLSCREEN
      );

      // Whenever the status bar and navigation bar appear, we want the playback controls to
      // appear as well.
      activity.getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
          new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int i) {
              // By doing a logical AND, we check if the fullscreen option is triggered (i.e. the
              // status bar is hidden). If the result of the logical AND is 0, that means that the
              // fullscreen flag is NOT triggered. This means that the status bar is showing. If
              // this is the case, then we show the playback controls as well (by calling show()).
              if ((i & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                show();
              }
            }
          }
      );

      container.setLayoutParams(Util.getLayoutParamsBasedOnParent(container,
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
      ));

      fullscreenButton.setImageResource(R.drawable.ic_action_return_from_full_screen);

      isFullscreen = true;
    }
  }

  public void enableSeeking() {
    this.canSeek = true;
    if (middleSection != null) {
      updateColors();
    }
  }

  public LayerManager getLayerManager() {
    return layerManager;
  }

  public void hide() {
    FrameLayout container = getLayerManager().getContainer();
    if (container == null) {
      return;
    }

    if (areControlsVisible) {
      container.removeView(view);

      // Make sure that the status bar and navigation bar are hidden when the playback controls
      // are hidden.
      if (isFullscreen) {
        getLayerManager().getActivity().getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN
        );
      }
      handler.removeMessages(SHOW_PROGRESS);
      areControlsVisible = false;
    }
  }

  public void hideTopChrome() {
    topChrome.setVisibility(View.GONE);
  }

  public boolean isFullscreen() {
    return isFullscreen;
  }

  public void setFullscreen(boolean shouldBeFullscreen) {
    if (shouldBeFullscreen != isFullscreen) {
      doToggleFullscreen();
    }
  }

  @Override
  public void onLayerDisplayed(LayerManager layerManager) {
    // We only want to show the playback control layer when the video is tapped, so display it
    // for 1 MILLISECOND and then make it disappear.
    // TODO(hsubrama): Figure out why playbackControlLayer.hide() doesn't work.
    show(1);
  }

  @Override
  public void onPause() {
    updatePlayPauseButton();
  }

  @Override
  public void onPlay() {
    updatePlayPauseButton();
  }

  public void setChromeColor(int color) {
    chromeColor = color;
    if (middleSection != null) {
      updateColors();
    }
  }

  public void setControlColor(int color) {
    this.controlColor = color;
    if (middleSection != null) {
      updateColors();
      updateActionButtons();
    }
  }

  public void setFullscreenCallback(FullscreenCallback fullscreenCallback) {
    this.fullscreenCallback = fullscreenCallback;
    if (fullscreenButton != null && fullscreenCallback != null) {
      fullscreenButton.setVisibility(View.VISIBLE);
    } else if (fullscreenButton != null && fullscreenCallback == null) {
      fullscreenButton.setVisibility(View.INVISIBLE);
    }
  }

  public void setLogoImageView(Drawable logo) {
    logoDrawable = logo;
    if (logoImageView != null) {
      logoImageView.setImageDrawable(logo);
    }
  }

  public void setPlayPause(boolean shouldPlay) {
    PlayerControl playerControl = getLayerManager().getControl();
    if (playerControl == null) {
      return;
    }

    if (shouldPlay) {
      playerControl.start();
    } else {
      playerControl.pause();
    }

    updatePlayPauseButton();
  }

  public void setSeekbarColor(int color) {
    this.seekbarColor = color;
    if (middleSection != null) {
      updateColors();
    }
  }

  public void setTextColor(int color) {
    this.textColor = color;
    if (middleSection != null) {
      updateColors();
    }
  }

  public void setVideoTitle(String title) {
    videoTitle = title;
    if (videoTitleView != null) {
      videoTitleView.setText(title);
    }
  }

  public void setVisibility(int visibility) {
    view.setVisibility(visibility);
  }

  /**
   * Perform binding to UI, setup of event handlers and initialization of values.
   */
  private void setupView() {
    // Bind fields to UI elements.
    pausePlayButton = (ImageButton) view.findViewById(R.id.pause);
    fullscreenButton = (ImageButton) view.findViewById((R.id.fullscreen));
    seekBar = (SeekBar) view.findViewById(R.id.mediacontroller_progress);
    videoTitleView = (TextView) view.findViewById(R.id.video_title);
    endTime = (TextView) view.findViewById(R.id.time_duration);
    currentTime = (TextView) view.findViewById(R.id.time_current);
    logoImageView = (ImageView) view.findViewById(R.id.logo_image);
    middleSection = (FrameLayout) view.findViewById(R.id.middle_section);
    topChrome = (RelativeLayout) view.findViewById(R.id.top_chrome);
    bottomChrome = (LinearLayout) view.findViewById(R.id.bottom_chrome);
    actionButtonsContainer = (LinearLayout) view.findViewById(R.id.actions_container);

    // The play button should toggle play/pause when the play/pause button is clicked.
    pausePlayButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        togglePause();
        show(DEFAULT_TIMEOUT_MS);
      }
    });

    if (fullscreenCallback == null) {
      fullscreenButton.setVisibility(View.INVISIBLE);
    }
    // Go into fullscreen when the fullscreen button is clicked.
    fullscreenButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        doToggleFullscreen();
        show(DEFAULT_TIMEOUT_MS);
        updateActionButtons();
        updateColors();
      }
    });

    seekBar.setMax(1000);

    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromuser) {
        if (!fromuser || !canSeek) {
          // Ignore programmatic changes to seek bar position.
          // Ignore changes to seek bar position is seeking is not enabled.
          return;
        }

        PlayerControl playerControl = getLayerManager().getControl();
        long duration = playerControl.getDuration();
        long newposition = (duration * progress) / 1000L;
        playerControl.seekTo((int) newposition);
        if (currentTime != null) {
          currentTime.setText(stringForTime((int) newposition));
        }
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
        show(0);
        isSeekbarDragging = true;
        handler.removeMessages(SHOW_PROGRESS);
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
        isSeekbarDragging = false;
        updateProgress();
        updatePlayPauseButton();
        show(DEFAULT_TIMEOUT_MS);

        handler.sendEmptyMessage(SHOW_PROGRESS);
      }
    });

    videoTitleView.setText(videoTitle);

    timeFormatBuilder = new StringBuilder();
    timeFormatter = new Formatter(timeFormatBuilder, Locale.getDefault());

  }

  public boolean shouldBePlaying() {
    return shouldBePlaying;
  }

  /**
   * Add the view back to the container. The playback controls disappear after timeout milliseconds.
   * @param timeout Hide the view after timeout milliseconds. If timeout == 0, then the playback
   *                controls will not disappear unless their container is tapped again.
   */
  public void show(int timeout) {
    if (!areControlsVisible && getLayerManager().getContainer() != null) {
      updateProgress();

      FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
          Gravity.CENTER
      );
      getLayerManager().getContainer().removeView(view);
      getLayerManager().getContainer().addView(view, layoutParams);
      setupView();
      areControlsVisible = true;
    }
    updatePlayPauseButton();

    handler.sendEmptyMessage(SHOW_PROGRESS);

    Message msg = handler.obtainMessage(FADE_OUT);
    if (timeout > 0) {
      handler.removeMessages(FADE_OUT);
      handler.sendMessageDelayed(msg, timeout);
    }
  }

  public void show() {
    show(DEFAULT_TIMEOUT_MS);
  }

  public void showTopChrome() {
    topChrome.setVisibility(View.VISIBLE);
    updateActionButtons();
    updateColors();
  }

  /**
   * Format the milliseconds to HH:MM:SS or MM:SS format.
   */
  public String stringForTime(int timeMs) {
    int totalSeconds = timeMs / 1000;

    int seconds = totalSeconds % 60;
    int minutes = (totalSeconds / 60) % 60;
    int hours = totalSeconds / 3600;

    timeFormatBuilder.setLength(0);
    if (hours > 0) {
      return timeFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
    } else {
      return timeFormatter.format("%02d:%02d", minutes, seconds).toString();
    }
  }

  public void togglePause() {
    this.shouldBePlaying = !getLayerManager().getControl().isPlaying();
    setPlayPause(shouldBePlaying);
  }

  /**
   * The action buttons are displayed in the top right of the video player. If the player is in
   * portrait mode, then display an overflow button which displays a dialog window containing the
   * possible actions. If the player is in landscape, then display the images for the actions in the
   * top right of the video player.
   */
  public void updateActionButtons() {
    actionButtonsContainer.removeAllViews();

    if (isFullscreen) {
      for (ImageButton imageButton : actionButtons) {
        actionButtonsContainer.addView(imageButton);
      }
    } else {
      ImageButton overflowButton = new ImageButton(getLayerManager().getActivity());
      overflowButton.setContentDescription(getLayerManager()
          .getActivity()
          .getString(R.string.overflow));
      overflowButton.setImageDrawable(getLayerManager()
          .getActivity()
          .getResources()
          .getDrawable(R.drawable.ic_action_overflow));

      AlertDialog.Builder builder = new AlertDialog.Builder(getLayerManager().getActivity());
      builder.setTitle(getLayerManager().getActivity().getString(R.string.select_an_action));
      final CharSequence[] actions = new CharSequence[actionButtons.size()];
      for (int i = 0; i < actionButtons.size(); i++) {
        actions[i] = actionButtons.get(i).getContentDescription();
      }
      builder.setItems(actions, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
          actionButtons.get(i).performClick();
        }
      });

      final AlertDialog alertDialog = builder.create();

      overflowButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          alertDialog.show();
        }
      });

      FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
      );
      int margin = 5 * getLayerManager()
          .getActivity()
          .getResources()
          .getDisplayMetrics()
          .densityDpi;
      layoutParams.setMargins(margin, 0, margin, 0);

      overflowButton.setBackgroundColor(Color.TRANSPARENT);
      overflowButton.setLayoutParams(layoutParams);
      overflowButton.setColorFilter(controlColor);
      actionButtonsContainer.addView(overflowButton);
    }
  }

  /**
   * Ensure that the chrome, control, and text colors displayed on the screen are correct.
   */
  public void updateColors() {
    currentTime.setTextColor(textColor);
    endTime.setTextColor(textColor);
    videoTitleView.setTextColor(textColor);

    fullscreenButton.setColorFilter(controlColor);
    pausePlayButton.setColorFilter(controlColor);
    seekBar.getProgressDrawable().setColorFilter(seekbarColor, PorterDuff.Mode.SRC_ATOP);
    seekBar.getThumb().setColorFilter(controlColor, PorterDuff.Mode.SRC_ATOP);

    // Hide the thumb drawable if the SeekBar is disabled
    if (canSeek) {
      seekBar.getThumb().mutate().setAlpha(255);
    } else {
      seekBar.getThumb().mutate().setAlpha(0);
    }

    for (ImageButton imageButton : actionButtons) {
      imageButton.setColorFilter(controlColor);
    }

    topChrome.setBackgroundColor(chromeColor);
    bottomChrome.setBackgroundColor(chromeColor);
    middleSection.setBackgroundColor(
        Color.argb(60,
            Color.red(chromeColor),
            Color.green(chromeColor),
            Color.blue(chromeColor)
        )
    );
  }

  /**
   * Change the icon of the play/pause button to indicate play or pause based on the state of the
   * video player.
   */
  public void updatePlayPauseButton() {
    PlayerControl playerControl = getLayerManager().getControl();
    if (view == null || pausePlayButton == null || playerControl == null) {
      return;
    }

    if (playerControl.isPlaying()) {
      pausePlayButton.setImageResource(R.drawable.ic_action_pause_large);
    } else {
      pausePlayButton.setImageResource(R.drawable.ic_action_play_large);
    }
  }

  /**
   * Adjust the position of the action bar to reflect the progress of the video.
   */
  public int updateProgress() {
    PlayerControl playerControl = getLayerManager().getControl();
    if (playerControl == null || isSeekbarDragging) {
      return 0;
    }

    int position = playerControl.getCurrentPosition();
    int duration = playerControl.getDuration();

    if (seekBar != null) {
      if (duration > 0) {
        long pos = 1000L * position / duration;
        seekBar.setProgress((int) pos);
      }

      int percent = playerControl.getBufferPercentage();
      seekBar.setSecondaryProgress(percent * 10);
    }

    if (endTime != null) {
      endTime.setText(stringForTime(duration));
    }
    if (currentTime != null) {
      currentTime.setText(stringForTime(position));
    }

    return position;
  }
}