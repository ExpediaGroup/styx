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
package com.hotels.styx.client;

import io.netty.channel.ChannelOption;

/**
 * A {@link ChannelOption} setting for to configure a {@link io.netty.channel.ChannelConfig}.
 *
 * @param <T> type of the setting's value.
 */
public final class ChannelOptionSetting<T> {
    private final ChannelOption<T> option;
    private final T value;

    /**
     * Construct an instance.
     *
     * @param option option
     * @param value value
     */
    public ChannelOptionSetting(ChannelOption<T> option, T value) {
        this.option = option;
        this.value = value;
    }

    /**
     * Channel option.
     *
     * @return option
     */
    public ChannelOption<T> option() {
        return option;
    }

    /**
     * Setting value.
     *
     * @return value
     */
    public T value() {
        return value;
    }
}
