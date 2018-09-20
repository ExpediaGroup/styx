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
package com.hotels.styx.api;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.testng.annotations.Test;
import rx.Observable;

import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ContentStreamTest {

//    @Test
//    public void peeksFirstNBytes() throws ExecutionException, InterruptedException {
//        ByteBuf buf1 = Unpooled.copiedBuffer("111111111111111111111111111111", UTF_8);
//        ByteBuf buf2 = Unpooled.copiedBuffer("222222222222222222222222222222", UTF_8);
//        ByteBuf buf3 = Unpooled.copiedBuffer("333333333333333333333333333333", UTF_8);
//
//        ContentStream stream = new RxContentStream(Observable.just(
//                buf1,
//                buf2,
//                buf3
//        ));
//
//
//        ByteBuf buf = stream.peek(20).get();
//
//        assertThat(buf, is(buf1));
//
//        String result = new String(stream.aggregate(1000).toBlocking().first(), UTF_8);
//
//        assertThat(result, is("111111111111111111111111111111" + "222222222222222222222222222222" + "333333333333333333333333333333"));
//    }
}