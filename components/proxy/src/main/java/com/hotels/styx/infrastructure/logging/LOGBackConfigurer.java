/*
  Copyright (C) 2013-2024 Expedia Inc.

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
package com.hotels.styx.infrastructure.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.LogbackException;
import ch.qos.logback.core.joran.spi.JoranException;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.zone.ZoneRulesException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static com.hotels.styx.common.Logging.sanitise;

/**
 * A utility class for managing configuration for a static LOGBack instance.
 *
 * @author Attila_Szili
 */
public final class LOGBackConfigurer {

    /**
     * Prefix for system property placeholders: "${".
     */
    private static final String PLACEHOLDER_PREFIX = "${";

    /**
     * Suffix for system property placeholders: "}".
     */
    private static final String PLACEHOLDER_SUFFIX = "}";

    /**
     * Pseudo URL prefix for loading from the class path: "classpath:".
     */
    private static final String CLASSPATH_URL_PREFIX = "classpath:";

    /**
     * URL prefix for loading from the file system: "file:".
     */
    private static final String FILE_URL_PREFIX = "file:";


    private LOGBackConfigurer() {
        // this class is not intended to be instantiated
    }

    /**
     * Initialize LOGBack from the given URL.
     *
     * @param logConfigLocation the path pointing to the location of the config file.
     * @param installJULBridge  set to true to install SLF4J JUL bridge
     * @throws IllegalArgumentException if the url points to a non existing location or an error occurs during the parsing operation.
     */
    public static void initLogging(String logConfigLocation, boolean installJULBridge) {
        try {
            String location = resolvePlaceholders(logConfigLocation);

            if (location.indexOf("${") >= 0) {
                throw new IllegalStateException("unable to resolve certain placeholders: " + sanitise(location));
            }
            // clean up location
            location = location.replaceAll("\\\\", "/");
            String notice = "If you are watching the console output, it may stop after this point, if configured to only write to file.";
            Logger.getLogger(LOGBackConfigurer.class.getName()).info("Initializing LOGBack from [" + sanitise(location) + "]. " + notice);

            initLogging(getURL(location), installJULBridge);

        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException("invalid '" + sanitise(logConfigLocation) + "' parameter: " + ex.getMessage());
        }
    }

    /**
     * Initialize LOGBack from the given URL.
     *
     * @param url              the url pointing to the location of the config file.
     * @param installJULBridge set to true to install SLF4J JUL bridge
     * @throws IllegalArgumentException if the url points to a non existing location or an error occurs during the parsing operation.
     */
    public static void initLogging(URL url, boolean installJULBridge) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
        try {
            configureByResource(url, loggerContext);
            loggerContext.start();
            if (installJULBridge) {
                //uninstall already present handlers we want to
                //continue logging through SLF4J after this point
                Logger l = LogManager.getLogManager().getLogger("");
                for (Handler h : l.getHandlers()) {
                    l.removeHandler(h);
                }
                SLF4JBridgeHandler.install();

            }
        } catch (JoranException | ZoneRulesException e) {
            Logger.getLogger(LOGBackConfigurer.class.getName()).log(Level.SEVERE, "exception while initializing LOGBack", e);
            throw new IllegalArgumentException("exception while initializing LOGBack", e);
        }
    }

    private static String resolvePlaceholders(String text) {
        StringBuilder buf = new StringBuilder(text);
        int startIndex = text.indexOf(PLACEHOLDER_PREFIX);
        while (startIndex != -1) {
            int endIndex = buf.indexOf(PLACEHOLDER_SUFFIX, startIndex + PLACEHOLDER_PREFIX.length());
            if (endIndex != -1) {
                String placeholder = buf.substring(startIndex + PLACEHOLDER_PREFIX.length(), endIndex);
                int nextIndex = endIndex + PLACEHOLDER_SUFFIX.length();
                String propVal = System.getProperty(placeholder);
                if (propVal == null) {
                    // Fall back to searching the system environment.
                    propVal = System.getenv(placeholder);
                }
                if (propVal != null) {
                    buf.replace(startIndex, endIndex + PLACEHOLDER_SUFFIX.length(), propVal);
                    nextIndex = startIndex + propVal.length();
                }
                startIndex = buf.indexOf(PLACEHOLDER_PREFIX, nextIndex);
            } else {
                startIndex = -1;
            }
        }
        return buf.toString();
    }

    private static URL getURL(String resourceLocation) throws FileNotFoundException {
        URL url;
        if (resourceLocation.startsWith(CLASSPATH_URL_PREFIX)) {
            String path = resourceLocation.substring(CLASSPATH_URL_PREFIX.length());
            url = getDefaultClassLoader().getResource(path);
            if (url == null) {
                String description = "class path resource [" + path + "]";
                throw new FileNotFoundException(description + " cannot be resolved to URL because it does not exist");
            }
        } else {
            try {
                // try URL
                url = new URL(resourceLocation);
            } catch (MalformedURLException ex) {
                // no URL -> treat as file path
                try {
                    url = new URL(FILE_URL_PREFIX + resourceLocation);
                } catch (MalformedURLException ex2) {
                    throw new FileNotFoundException("Resource location [" + resourceLocation + "] is neither a URL not a well-formed file path");
                }
            }
        }
        return url;
    }

    // Logback interface has changed by removing this method. Copying from https://github.com/qos-ch/logback/commit/4b06e062488e4cb87f22be6ae96e4d7d6350ed6b
    private static void configureByResource(URL url, Context loggerContext) throws JoranException {
        if (url == null) {
            throw new IllegalArgumentException("URL argument cannot be null");
        }
        final String urlString = url.toString();
        if (urlString.endsWith("xml")) {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(loggerContext);
            configurator.doConfigure(url);
        } else {
            throw new LogbackException("Unexpected filename extension of file [" + url + "]. Should be .xml");
        }
    }

    private static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ex) {
            cl = LOGBackConfigurer.class.getClassLoader();
        }
        if (cl == null) {
            // No thread context class loader -> use class loader of this class.
            cl = LOGBackConfigurer.class.getClassLoader();
        }
        return cl;
    }
}
