/**
 * Copyright 2014 Google Inc. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.libraries.mediaframework.exoplayerextensions;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.extractor.mp4.Mp4Extractor;

/**
 * Generate a renderer builder appropriate for rendering a video.
 */
public class RendererBuilderFactory {

    /**
     * Create a renderer builder which can build the given video.
     * @param ctx The context (ex {@link android.app.Activity} in whicb the video has been created.
     * @param video The video which will be played.
     */
    public static ExoplayerWrapper.RendererBuilder createRendererBuilder(Context ctx,
                                                                         Video video) {
        String userAgent = ExoplayerUtil.getUserAgent(ctx);

        switch (video.getVideoType()) {
            case HLS:
                return new HlsRendererBuilder(ctx,
                        userAgent,
                        video.getUrl(),
                        null);
            case DASH:
                return new DashRendererBuilder(ctx,
                        userAgent,
                        video.getUrl(),
                        new WidevineTestMediaDrmCallback(video.getContentId()),
                        null, /* TODO: Pass in DebugTextView here. */
                        null);
            case MP4:
                return new ExtractorRendererBuilder(ctx, userAgent, Uri.parse(video.getUrl()),
                        null, /* TODO: DebugTextView. */
                        new Mp4Extractor());
            default:
                return null;
        }
    }
}
