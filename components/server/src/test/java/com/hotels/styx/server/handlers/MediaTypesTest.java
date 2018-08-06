/*
  Copyright (C) 2013-2018 Expedia Inc.

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
package com.hotels.styx.server.handlers;

import com.google.common.net.MediaType;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.google.common.net.MediaType.CSS_UTF_8;
import static com.google.common.net.MediaType.GIF;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JAVASCRIPT_UTF_8;
import static com.google.common.net.MediaType.JPEG;
import static com.google.common.net.MediaType.MICROSOFT_EXCEL;
import static com.google.common.net.MediaType.MPEG_AUDIO;
import static com.google.common.net.MediaType.MPEG_VIDEO;
import static com.google.common.net.MediaType.OCTET_STREAM;
import static com.google.common.net.MediaType.PDF;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.net.MediaType.PNG;
import static com.hotels.styx.server.handlers.MediaTypes.ICON;
import static com.hotels.styx.server.handlers.MediaTypes.MICROSOFT_ASF_VIDEO;
import static com.hotels.styx.server.handlers.MediaTypes.MICROSOFT_MS_VIDEO;
import static com.hotels.styx.server.handlers.MediaTypes.WAV_AUDIO;
import static com.hotels.styx.server.handlers.MediaTypes.mediaTypeForExtension;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class MediaTypesTest {

    @Test(dataProvider = "mediaTypesByFileExtensions")
    public void returnsMediaTypesByFileExtensions(String extension, MediaType mediaType) {
        assertThat(mediaTypeForExtension(extension), is(mediaType));
    }


    @DataProvider(name = "mediaTypesByFileExtensions")
    protected Object[][] mediaTypesByFileExtensions() {
        return new Object[][]{
                {"htm", HTML_UTF_8},
                {"txt", PLAIN_TEXT_UTF_8},
                {"unknown", OCTET_STREAM},
                {"css", CSS_UTF_8},
                {"js", JAVASCRIPT_UTF_8},

                {"ico", ICON},
                {"gif", GIF},
                {"jpg", JPEG},
                {"jpeg", JPEG},
                {"png", PNG},
                {"xls", MICROSOFT_EXCEL},
                {"pdf", PDF},

                {"mp3", MPEG_AUDIO},
                {"wav", WAV_AUDIO},

                {"asf", MICROSOFT_ASF_VIDEO},
                {"avi", MICROSOFT_MS_VIDEO},
                {"mpg", MPEG_VIDEO}
        };
    }
}