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
package com.hotels.styx.api;

import com.hotels.styx.common.EventProcessor;
import com.hotels.styx.common.FsmEventProcessor;
import com.hotels.styx.common.QueueDrainingEventProcessor;
import com.hotels.styx.common.StateMachine;
import rx.Observable;

import java.util.function.Consumer;

/**
 * Associate callbacks to Streaming Response object.
 */
public class ResponseEventListener {
    private final Observable<HttpResponse> publisher;
    private Runnable cancelAction;
    private Consumer<Throwable> responseErrorAction;
    private Consumer<Throwable> contentErrorAction;
    private Runnable onCompletedAction;

    private final StateMachine<State> fsm = new StateMachine.Builder<State>()
            .initialState(State.INITIAL)
            .transition(State.INITIAL, MessageHeaders.class, event -> State.STREAMING)
            .transition(State.INITIAL, MessageCancelled.class, event -> {
                cancelAction.run();
                return State.TERMINATED;
            })
            .transition(State.INITIAL, MessageError.class, event -> {
                responseErrorAction.accept(event.cause());
                return State.TERMINATED;
            })
            .transition(State.STREAMING, ContentEnd.class, event -> {
                onCompletedAction.run();
                return State.COMPLETED;
            })
            .transition(State.STREAMING, ContentError.class, event -> {
                contentErrorAction.accept(event.cause());
                return State.TERMINATED;
            })
            .transition(State.STREAMING, ContentCancelled.class, event -> {
                cancelAction.run();
                return State.TERMINATED;
            })
            .onInappropriateEvent((state, event) -> state)
            .build();

    ResponseEventListener(Observable<HttpResponse> publisher) {
        this.publisher = publisher;
    }

    public static ResponseEventListener from(rx.Observable<HttpResponse> publisher) {
        return new ResponseEventListener(publisher);
    }

    public ResponseEventListener whenCancelled(Runnable action) {
        this.cancelAction = action;
        return this;
    }

    public ResponseEventListener whenResponseError(Consumer<Throwable> responseErrorAction) {
        this.responseErrorAction = responseErrorAction;
        return this;
    }

    public ResponseEventListener whenContentError(Consumer<Throwable> contentErrorAction) {
        this.contentErrorAction = contentErrorAction;
        return this;
    }

    public Observable<HttpResponse> apply() {
        EventProcessor eventProcessor = new QueueDrainingEventProcessor(
                new FsmEventProcessor<>(fsm, (throwable, state) -> {
                }, ""));

        return publisher
                .doOnNext(headers -> eventProcessor.submit(new MessageHeaders()))
                .doOnError(cause -> eventProcessor.submit(new MessageError(cause)))
                .doOnUnsubscribe(() -> eventProcessor.submit(new MessageCancelled()))
                .map(response -> Requests.doOnError(response, cause -> eventProcessor.submit(new ContentError(cause))))
                .map(response -> Requests.doOnComplete(response, () -> eventProcessor.submit(new ContentEnd())))
                .map(response -> Requests.doOnCancel(response, () -> eventProcessor.submit(new ContentCancelled())));

    }

    public ResponseEventListener whenCompleted(Runnable action) {
        this.onCompletedAction = action;
        return this;
    }

    enum State {
        INITIAL,
        STREAMING,
        TERMINATED,
        COMPLETED
    }


    static class MessageHeaders {

    }

    static class MessageError {
        private Throwable cause;

        public MessageError(Throwable cause) {

            this.cause = cause;
        }

        public Throwable cause() {
            return cause;
        }
    }

    static class MessageCancelled {

    }

    static class ContentEnd {

    }

    static class ContentError {
        private Throwable cause;

        public ContentError(Throwable cause) {

            this.cause = cause;
        }

        public Throwable cause() {
            return cause;
        }
    }

    static class ContentCancelled {

    }
}
