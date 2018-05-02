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
package com.hotels.styx.spi;

import com.hotels.styx.spi.config.SpiExtensionFactory;
import com.hotels.styx.support.CannotInstantiate;
import org.testng.annotations.Test;

import static com.hotels.styx.spi.ClassSource.fromClassLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

public class ExtensionObjectFactoryTest {
    private static final ClassSource DEFAULT_CLASS_LOADER = fromClassLoader(ExtensionObjectFactoryTest.class.getClassLoader());

    @Test(expectedExceptions = ExtensionLoadingException.class, expectedExceptionsMessageRegExp = "no class=some.FakeClass is found in the specified classpath=/fake/class/path")
    public void throwsAppropriateExceptionIfClassNotFound() {
        ExtensionObjectFactory factory = new ExtensionObjectFactory(extensionFactory -> name -> {
            throw new ClassNotFoundException();
        });

        factory.newInstance(new SpiExtensionFactory("some.FakeClass", "/fake/class/path"), Object.class);
    }

    @Test
    public void throwsAppropriateExceptionIfObjectCannotBeInstantiated() {
        ExtensionObjectFactory factory = new ExtensionObjectFactory(extensionFactory -> DEFAULT_CLASS_LOADER);
        String className = CannotInstantiate.class.getName();

        try {
            factory.newInstance(new SpiExtensionFactory(className, "/fake/class/path"), Object.class);
            fail("No exception thrown");
        } catch (ExtensionLoadingException e) {
            assertThat(e.getMessage(), is("error instantiating class=" + className));
            assertThat(e.getCause(), is(instanceOf(IllegalAccessException.class)));
        }
    }

    @Test
    public void instantiatesObjects() {
        ExtensionObjectFactory factory = new ExtensionObjectFactory(extensionFactory -> DEFAULT_CLASS_LOADER);
        String className = String.class.getName();
        Object object = factory.newInstance(new SpiExtensionFactory(className, "/fake/class/path"), Object.class);

        assertThat(object, is(instanceOf(String.class)));
    }
}