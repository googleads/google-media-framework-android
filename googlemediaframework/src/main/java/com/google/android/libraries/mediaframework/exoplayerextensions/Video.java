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

/**
 * Encapsulates a video that can be played by Exoplayer.
 */
public class Video {
  public static enum VideoType {
    DASH_VOD,
    OTHER
  }

  private final String url;
  private final VideoType videoType;

  /**
   * ID of content (for DASH).
   */
  private final String contentId;

  public Video(String url, VideoType videoType) {
    this(url, videoType, null);
  }

  public Video(String url, VideoType videoType, String contentId) {
    this.url = url;
    this.videoType = videoType;
    this.contentId = contentId;
  }

  public String getContentId() {
    return contentId;
  }

  public String getUrl() {
    return url;
  }

  public VideoType getVideoType() {
    return videoType;
  }
}
