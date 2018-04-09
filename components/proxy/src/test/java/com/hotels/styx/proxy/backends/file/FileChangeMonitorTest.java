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
package com.hotels.styx.proxy.backends.file;

import org.slf4j.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static ch.qos.logback.classic.Level.INFO;
import static com.google.common.io.Files.createTempDir;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.delete;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.slf4j.LoggerFactory.getLogger;


public class FileChangeMonitorTest {
    private static final Logger LOGGER = getLogger(FileChangeMonitorTest.class);

    private File tempDir;
    private Path monitoredFile;
    private FileMonitor.Listener listener;
    private FileChangeMonitor monitor;

    @BeforeMethod
    public void setUp() throws Exception {
        tempDir = createTempDir();
        monitoredFile = Paths.get(tempDir.toString(), "origins.yml");
        write(monitoredFile, "content-v0");
        listener = mock(FileChangeMonitor.Listener.class);
        monitor = new FileChangeMonitor(monitoredFile.toString(), 250, MILLISECONDS);
        ((ch.qos.logback.classic.Logger) getLogger(FileChangeMonitor.class)).setLevel(INFO);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        monitor.stop();
        try {
            delete(monitoredFile);
        } catch (java.nio.file.NoSuchFileException cause) {
            // Pass ...
        }

        delete(tempDir.toPath());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void throwExceptionIfFileDoesNotExist() {
        new FileChangeMonitor("/nonexistant/file");
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "File monitor for '.*' is already started")
    public void canBeStartedOnlyOnce() {
        FileChangeMonitor.Listener listener = mock(FileChangeMonitor.Listener.class);
        FileChangeMonitor monitor = new FileChangeMonitor(monitoredFile.toString());

        monitor.start(listener);
        monitor.start(listener);
    }

    @Test
    public void initialPollNotifiesListeners() {
        monitor.start(listener);
        verify(listener, timeout(3000).times(1)).fileChanged();
    }


    @Test
    public void notifiesListenersOnFileChange() throws Exception {
        monitor.start(listener);
        verify(listener, timeout(3000).times(1)).fileChanged();

        Thread.sleep(250);

        for (int i = 2; i < 10; i++) {
            write(monitoredFile, format("content-v%d", i));
            verify(listener, timeout(3000).times(i)).fileChanged();
            LOGGER.info(format("verified v%d", i));
        }
    }

    @Test
    public void recoversFromFileDeletions() throws Exception {
        monitor.start(listener);
        verify(listener, timeout(3000).times(1)).fileChanged();

        delete(monitoredFile);
        Thread.sleep(2000);

        write(monitoredFile, "some new content");
        verify(listener, timeout(3000).times(2)).fileChanged();
    }

    @Test
    public void detectsFileSizeChanges() throws Exception {
        monitor.start(listener);
        verify(listener, timeout(3000).times(1)).fileChanged();
    }

    @Test
    public void recoversFromTruncatedFiles() throws Exception {
        monitor.start(listener);
        verify(listener, timeout(3000).times(1)).fileChanged();

        write(monitoredFile, "");
        verify(listener, timeout(3000).times(2)).fileChanged();

        write(monitoredFile, "some new content");
        verify(listener, timeout(3000).times(3)).fileChanged();
    }

    void write(Path path, String text) throws Exception {
        LOGGER.info("Writing to temporary file '{}", path);
        LOGGER.info(text);
        copy(new ByteArrayInputStream(text.getBytes(UTF_8)), path, REPLACE_EXISTING);
    }

}
