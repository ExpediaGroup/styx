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
package com.hotels.styx.support;

import org.testng.FileAssert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ClassPathResourceUtilsTest {

    private final String testFolder = getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
    private final File regularName = new File(testFolder, "regularname.txt");
    private final File spacesInName = new File(testFolder, "name with spaces.txt");

    @AfterClass
    public void cleanUpResources() {
        try {
            regularName.delete();
            spacesInName.delete();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void canRetrieveFile(File testedFile) throws IOException {
        testedFile.createNewFile();
        FileAssert.assertFile(testedFile);
        File result = new File(
                ClassPathResourceUtils.getResource(getClass(), "/" + testedFile.getName())
        );
        FileAssert.assertFile(result);
        assertThat(result, is(testedFile));
    }

    @Test
    public void canRetrieveFileWithRegularName() throws IOException {
        canRetrieveFile(regularName);

    }

    @Test
    public void canRetrieveFileWithSpacesInName() throws IOException {
        canRetrieveFile(spacesInName);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* not found")
    public void nonExistingFileCannotBeRetrieved() {
        ClassPathResourceUtils.getResource(getClass(), "Nonexisting");
    }
}