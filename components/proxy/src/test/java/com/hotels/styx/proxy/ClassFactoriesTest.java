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
package com.hotels.styx.proxy;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClassFactoriesTest {
    @Test
    public void instantiatesClassWithZeroArgumentConstructor() {
        MyInterface instance = ClassFactories.newInstance(MyClass.class.getName(), MyInterface.class);

        assertThat(instance, is(instanceOf(MyClass.class)));
    }

    @Test
    public void throwsExceptionIfThereIsNoZeroArgumentConstructor() {
        Exception e = assertThrows(RuntimeException.class,
                () -> ClassFactories.newInstance(MyInvalidClass.class.getName(), MyInterface.class));
        assertEquals("java.lang.InstantiationException: com.hotels.styx.proxy.ClassFactoriesTest$MyInvalidClass", e.getMessage());
    }

    @Test
    public void throwsExceptionIfClassDoesNotExtendType() {
        Exception e = assertThrows(ClassCastException.class,
                () -> ClassFactories.newInstance(MyClass.class.getName(), Runnable.class));
        assertEquals("Cannot cast com.hotels.styx.proxy.ClassFactoriesTest$MyClass to java.lang.Runnable", e.getMessage());
    }

    @Test
    public void throwsExceptionIfClassDoesNotExist() {
        Exception e = assertThrows(IllegalArgumentException.class,
                () -> ClassFactories.newInstance(MyClass.class.getName() + "NonExistent", Runnable.class));
        assertEquals("No such class 'com.hotels.styx.proxy.ClassFactoriesTest$MyClassNonExistent'", e.getMessage());
    }

    public interface MyInterface {
    }

    public static class MyClass implements MyInterface {
    }

    public static class MyInvalidClass implements MyInterface {
        public MyInvalidClass(String foo) {
        }
    }
}