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
package com.hotels.styx.api;

import com.hotels.styx.common.EventProcessor;
import com.hotels.styx.common.QueueDrainingEventProcessor;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.function.Consumer;

import static com.hotels.styx.api.ResponseEventListener.State.INITIAL;
import static com.hotels.styx.api.ResponseEventListener.State.COMPLETED;
import static com.hotels.styx.api.ResponseEventListener.State.STREAMING;
import static com.hotels.styx.api.ResponseEventListener.State.TERMINATED;
import static java.util.Objects.requireNonNull;

/**
 * Associate callbacks to Streaming Response object.
 */
public class ResponseEventListener {
    private final Flux<LiveHttpResponse> publisher;
    private Consumer<Throwable> responseErrorAction = cause -> { };
    private Consumer<Throwable> contentErrorAction = cause -> { };
    private Consumer<LiveHttpResponse> onCompletedAction = r -> { };
    private Runnable cancelAction = () -> { };
    private Runnable onHeaders = () -> { };
    private Runnable whenFinishedAction = () -> { };

    private volatile State state = INITIAL;

    private ResponseEventListener(Publisher<LiveHttpResponse> publisher) {
        this.publisher = Flux.from(requireNonNull(publisher));
    }

    public static ResponseEventListener from(Publisher<LiveHttpResponse> publisher) {
        return new ResponseEventListener(publisher);
    }

    public ResponseEventListener whenCancelled(Runnable action) {
        this.cancelAction = requireNonNull(action);
        return this;
    }

    public ResponseEventListener whenResponseError(Consumer<Throwable> responseErrorAction) {
        this.responseErrorAction = requireNonNull(responseErrorAction);
        return this;
    }

    public ResponseEventListener whenContentError(Consumer<Throwable> contentErrorAction) {
        this.contentErrorAction = requireNonNull(contentErrorAction);
        return this;
    }

    public ResponseEventListener whenCompleted(Consumer<LiveHttpResponse> completeAction) {
        this.onCompletedAction = requireNonNull(completeAction);
        return this;
    }

    public ResponseEventListener whenHeadersComplete(Runnable action) {
        this.onHeaders = requireNonNull(action);
        return this;
    }

    /**
     * Executes an action when the response terminates for any reason, normally,
     * abnormally, or due to cancellation.
     *
     * @param action a runnable action
     * @return the builder
     */
    public ResponseEventListener whenFinished(Runnable action) {
        this.whenFinishedAction = requireNonNull(action);
        return this;
    }

    public Flux<LiveHttpResponse> apply() {

        EventProcessor eventProcessor = new QueueDrainingEventProcessor(
                event -> {
                    switch (state) {
                        case INITIAL:
                            if (event instanceof MessageHeaders) {
                                onHeaders.run();
                                state = STREAMING;
                            } else if (event instanceof MessageCancelled) {
                                cancelAction.run();
                                whenFinishedAction.run();
                                state = TERMINATED;
                            } else if (event instanceof MessageCompleted) {
                                // TODO: Add custom exception type?
                                responseErrorAction.accept(new RuntimeException("Response Observable completed without message headers."));
                                whenFinishedAction.run();
                                state = TERMINATED;
                            } else if (event instanceof MessageError) {
                                responseErrorAction.accept(((MessageError) event).cause());
                                whenFinishedAction.run();
                                state = TERMINATED;
                            }

                            break;
                        case STREAMING:
                            if (event instanceof ContentEnd) {
                                onCompletedAction.accept(((ContentEnd) event).response);
                                whenFinishedAction.run();
                                state = COMPLETED;
                            } else if (event instanceof ContentError) {
                                contentErrorAction.accept(((ContentError) event).cause());
                                whenFinishedAction.run();
                                state = TERMINATED;
                            } else if (event instanceof ContentCancelled) {
                                cancelAction.run();
                                whenFinishedAction.run();
                                state = TERMINATED;
                            }

                            break;
                    }
                });

        return publisher
                .doOnNext(headers -> eventProcessor.submit(new MessageHeaders()))
                .doOnComplete(() -> eventProcessor.submit(new MessageCompleted()))
                .doOnError(cause -> eventProcessor.submit(new MessageError(cause)))
                .doOnCancel(() -> eventProcessor.submit(new MessageCancelled()))
                .map(response ->
                    response.newBuilder()
                            .body(it -> it.doOnEnd(ifError(cause -> eventProcessor.submit(new ContentError(cause)))))
                            .body(it -> it.doOnEnd(ifSuccessful(() -> eventProcessor.submit(new ContentEnd(response)))))
                            .body(it -> it.doOnCancel(() -> eventProcessor.submit(new ContentCancelled())))
                            .build());
    }

    enum State {
        INITIAL,
        STREAMING,
        TERMINATED,
        COMPLETED
    }

    private static Consumer<Optional<Throwable>> ifError(Consumer<Throwable> action) {
        return maybeCause -> maybeCause.ifPresent(action);
    }

    private static Consumer<Optional<Throwable>> ifSuccessful(Runnable action) {
        return maybeCause -> {
            if (!maybeCause.isPresent()) {
                action.run();
            }
        };
    }

    private static class MessageHeaders {

    }

    private static class MessageError {
        private Throwable cause;

        public MessageError(Throwable cause) {

            this.cause = cause;
        }

        public Throwable cause() {
            return cause;
        }
    }

    private static class MessageCompleted {

    }

    private static class MessageCancelled {

    }

    private static class ContentEnd {
        private final LiveHttpResponse response;
        public ContentEnd(LiveHttpResponse response) {
            this.response = response;
        }
    }

    private static class ContentError {
        private Throwable cause;

        public ContentError(Throwable cause) {

            this.cause = cause;
        }

        public Throwable cause() {
            return cause;
        }
    }

    private static class ContentCancelled {

    }
}
