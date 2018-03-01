/**
 * Copyright (C) 2013-2018 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.proxy.backends.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.io.Files.createTempDir;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;


public class FileChangeMonitorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileChangeMonitorTest.class);

    private File tempDir;
    private Path monitoredFile;

    @BeforeMethod
    public void setUp() throws Exception {
        tempDir = createTempDir();
        monitoredFile = Paths.get(tempDir.toString(), "origins.yml");
        write(monitoredFile, "content-v1");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        Files.delete(monitoredFile);
        Files.delete(tempDir.toPath());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void throwExceptionIfFileDoesNotExist() throws Exception {
        new FileChangeMonitor("/nonexistant/file");
    }

    @Test
    public void notifiesListenersOnFileChange() throws Exception {
        FileChangeMonitor.Listener listener = mock(FileChangeMonitor.Listener.class);
        FileChangeMonitor monitor = new FileChangeMonitor(monitoredFile.toString());

        LOGGER.info("Monitored file: " + monitoredFile);
        monitor.start(listener);

        // TODO: these Thread.sleep()s are necessary, otherwise the watcher doesn't pick up the change,
        // as if the monitored file gets written out just before the monitoring actually starts.
        Thread.sleep(1000);

        write(monitoredFile, "content-v2");
        verify(listener, timeout(30000).times(1)).fileChanged();
        LOGGER.info("verified v2");

        write(monitoredFile, "content-v3");
        verify(listener, timeout(30000).times(2)).fileChanged();
        LOGGER.info("verified v3");
    }

    @Test
    public void processesCascadingEvents() throws Exception {
        FileChangeMonitor.Listener listener = mock(FileChangeMonitor.Listener.class);
        FileChangeMonitor monitor = new FileChangeMonitor(monitoredFile.toString());

        LOGGER.info("Monitored file: " + monitoredFile);
        monitor.start(listener);

        // TODO: these Thread.sleep()s are necessary, otherwise the watcher doesn't pick up the change,
        // as if the monitored file gets written out just before the monitoring actually starts.
        Thread.sleep(1000);

        write(monitoredFile, "content-v2");
        verify(listener, timeout(30000).times(1)).fileChanged();
        LOGGER.info("verified v2");

        write(monitoredFile, "content-v3");
        write(monitoredFile, "content-v4");
        write(monitoredFile, "content-v5");
        write(monitoredFile, "content-v6");

        verify(listener, timeout(30000).times(2)).fileChanged();
        LOGGER.info("verified v3");
    }

    void write(Path path, String text) throws Exception {
        LOGGER.info("Updating temporary file '{}", path);
        LOGGER.info(text);
        Files.copy(new ByteArrayInputStream(text.getBytes(UTF_8)), path, REPLACE_EXISTING);
    }

}
