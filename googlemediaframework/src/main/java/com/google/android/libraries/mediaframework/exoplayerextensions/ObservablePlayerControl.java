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

package com.google.android.libraries.mediaframework.exoplayerextensions;

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.util.PlayerControl;

import java.util.ArrayList;
import java.util.List;

public class ObservablePlayerControl extends PlayerControl {
  List<PlayerControlCallback> callbacks;

  public ObservablePlayerControl(ExoPlayer exoPlayer) {
    super(exoPlayer);
    callbacks = new ArrayList<PlayerControlCallback>();
  }

  public void addCallback(PlayerControlCallback callback) {
    callbacks.add(callback);
  }

  @Override
  public void pause() {
    super.pause();
    for (PlayerControlCallback callback : callbacks) {
      callback.onPause();
    }
  }

  public void removeCallback(PlayerControlCallback callback) {
    callbacks.remove(callback);
  }

  @Override
  public void start() {
    super.start();
    for (PlayerControlCallback callback : callbacks) {
      callback.onPlay();
    }
  }

}
