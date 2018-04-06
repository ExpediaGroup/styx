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

import com.hotels.styx.api.UrlQuery.Parameter;
import org.testng.annotations.Test;

import static com.hotels.styx.support.matchers.IsOptional.isAbsent;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class UrlQueryTest {
    private final UrlQuery query = new UrlQuery.Builder()
            .addParam("foo", "alpha")
            .addParam("bar", "beta")
            .addParam("foo", "gamma")
            .build();

    @Test
    public void providesEncodedQuery() {
        assertThat(query.encodedQuery(), is("foo=alpha&bar=beta&foo=gamma"));
    }

    @Test
    public void providesParameterNames() {
        assertThat(query.parameterNames(), contains("foo", "bar"));
    }

    @Test
    public void providesValueByKey() {
        assertThat(query.parameterValue("foo"), isValue("alpha"));
        assertThat(query.parameterValue("bar"), isValue("beta"));
        assertThat(query.parameterValue("no_such_key"), isAbsent());
    }

    @Test
    public void providesValuesByKey() {
        assertThat(query.parameterValues("foo"), contains("alpha", "gamma"));
        assertThat(query.parameterValues("bar"), contains("beta"));
        assertThat(query.parameterValues("no_such_key"), is(emptyIterable()));
    }

    @Test
    public void providesFullParameters() {
        assertThat(query.parameters(), contains(
                new Parameter("foo", "alpha"),
                new Parameter("bar", "beta"),
                new Parameter("foo", "gamma")
        ));
    }

    @Test
    public void buildsNewQueryFromExistingQuery() {
        assertThat(query.newBuilder().build(), equalTo(query));
    }

    @Test
    public void newQueryBuilderAllowsParameterToBeAdded() {
        UrlQuery newQuery = query.newBuilder()
                .addParam("new_param", "delta")
                .build();

        assertThat(newQuery.parameters(), contains(
                new Parameter("foo", "alpha"),
                new Parameter("bar", "beta"),
                new Parameter("foo", "gamma"),
                new Parameter("new_param", "delta")
        ));
    }

    @Test
    public void makesQueryFromString() {
        UrlQuery query = new UrlQuery.Builder("foo=alpha&bar=beta&foo=gamma")
                .build();

        assertThat(query.parameterNames(), contains("foo", "bar"));

        assertThat(query.parameterValue("foo"), isValue("alpha"));
        assertThat(query.parameterValue("bar"), isValue("beta"));

        assertThat(query.parameterValues("foo"), contains("alpha", "gamma"));

        assertThat(query.parameters(), containsInAnyOrder(
                new Parameter("foo", "alpha"),
                new Parameter("bar", "beta"),
                new Parameter("foo", "gamma")
        ));

        assertThat(query.encodedQuery(), is("foo=alpha&foo=gamma&bar=beta"));
    }

    @Test
    public void acceptsEmptyQueryString() {
        UrlQuery query = new UrlQuery.Builder("")
                .build();

        assertThat(query.encodedQuery(), is(""));
        assertThat(query.parameters().isEmpty(), is(true));
    }
}