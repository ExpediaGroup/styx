/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.common.format;

import com.hotels.styx.api.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SanitisedHttpHeaderFormatterTest {

    @Test
    public void formatShouldFormatRequest() {

        HttpHeaders headers = new HttpHeaders.Builder()
                .add("header1", "a")
                .add("header2", "b")
                .add("header3", "c")
                .add("header4", "d")
                .add("COOKIE", "cookie1=e;cookie2=f;")
                .add("SET-COOKIE", "cookie3=g;cookie4=h;")
                .build();

        List<String> headersToHide = Arrays.asList("HEADER1", "HEADER3");
        List<String> cookiesToHide = Arrays.asList("cookie2", "cookie4");
        String formattedHeaders = new SanitisedHttpHeaderFormatter(headersToHide, cookiesToHide).format(headers);

        assertThat(formattedHeaders,
                is("header1=****, header2=b, header3=****, header4=d, COOKIE=cookie1=e;cookie2=****, SET-COOKIE=cookie3=g;cookie4=****"));
    }

}