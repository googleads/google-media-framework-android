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

import android.widget.FrameLayout;

/**
 * Creates a custom view which can be added on top of a LayerManager.
 *
 * <p>In order to ensure that layers' views can be overlaid ON TOP of each other, they must be of
 * type {@link FrameLayout}.
 *
 * <p>See {@link SubtitleLayer}, {@link VideoSurfaceLayer}, and {@link PlaybackControlLayer}
 * for examples.
 */
public interface Layer {
  public FrameLayout createView(LayerManager layerManager);

  /**
   * Called when a Layer's view has been displayed on the screen. Any additional setup which
   * can only be performed after the layer is displayed should be done here.
   */
  public void onLayerDisplayed(LayerManager layerManager);
}
