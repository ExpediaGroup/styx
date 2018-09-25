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
package com.hotels.styx;

import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ItemsDumpTest {
    @Test
    public void formatsAsLines() {
        String string = ItemsDump.dump(asList("Alpha", "Beta", "Gamma")).toString();

        assertThat(string, is("Alpha\nBeta\nGamma\n"));
    }

    @Test
    public void sorts() {
        String string = ItemsDump.dump(asList("Beta", "Alpha", "Gamma")).toString();

        assertThat(string, is("Alpha\nBeta\nGamma\n"));
    }

    @Test
    public void addsIndentation() {
        String string = ItemsDump.dump(asList("Alpha", "Beta", "Gamma"))
                .indentWith("**")
                .toString();

        assertThat(string, is("**Alpha\n**Beta\n**Gamma\n"));
    }

    @Test
    public void filters() {
        String string = ItemsDump.dump(asList("Alpha", "Beta", "Gamma", "Betb"))
                .filter("Bet")
                .toString();

        assertThat(string, is("Beta\nBetb\n"));
    }

    @Test
    public void findsDifferences() {
        ItemsDump one = ItemsDump.dump(asList("Alpha", "Beta", "Gamma", "Alpha", "Alpha"));
        ItemsDump two = ItemsDump.dump(asList("Alpha", "Beta", "Delta"));

        String string = two.diff(one).toString();

        assertThat(string, is(""
                + "+ Delta\n"
                + "- Alpha\n"
                + "- Alpha\n"
                + "- Gamma\n"
        ));
    }
}