/*
  Copyright (C) 2013-2019 Expedia Inc.

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
package com.hotels.styx.common.testing;

/**
 * A way to check that expected exceptions have been thrown, without needing to exit the test method.
 */
public final class ExceptionExpectation {
    private ExceptionExpectation() {
    }

    /**
     * Runs the given action and validates whether it matches the expected exception type.
     * If it does, the exception is returned.
     * Otherwise, an AssertionError is thrown.
     *
     * @param exceptionType an exception type
     * @param action an action
     * @param <E> an exception type
     * @return the exception
     */
    public static <E extends Throwable> E expect(Class<E> exceptionType, Action action) {
        try {
            action.run();
        } catch (Throwable e) {
            if (exceptionType.isInstance(e)) {
                return (E) e;
            }

            if (e instanceof Error) {
                throw (Error) e;
            }

            throw new AssertionError("Unexpected exception thrown. Expected " + exceptionType.getName() + ", but got " + e.getClass().getName(), e);
        }

        throw new AssertionError("Expected exception " + exceptionType.getName() + " was not thrown.");
    }

    /**
     * Like {@link Runnable}, but can throw checked exceptions.
     */
    public interface Action {
        void run() throws Exception;
    }
}
