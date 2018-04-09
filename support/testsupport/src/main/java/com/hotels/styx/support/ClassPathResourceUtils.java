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

import java.net.URISyntaxException;
import java.nio.file.Paths;

public class ClassPathResourceUtils {

    /**
     * Finds a resource with a given name starting the search from baseClass.
     * This method delegates to {@link Class#getResource}
     * @param baseClass class from which the search process will start.
     * @param path name of(or relative path to) the desired resource.
     * @return absolute path to the specified resource.
     * @throws IllegalArgumentException if the specified resource cannot be found.
     */
    public static String getResource(Class baseClass, String path){
        try {
            return Paths.get(baseClass.getResource(path).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (NullPointerException e){
            throw new IllegalArgumentException(path +" not found", e);
        }
    }
}
