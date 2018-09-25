package com.hotels.styx.api.stream;

import com.hotels.styx.api.Buffer;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ContentProcessor implements Processor<Buffer, Buffer> {

    /*
     * 1. A Subscriber MUST signal demand via Subscription.request(long n) to receive onNext signals.
     *
     * 2. If a Subscriber suspects that its processing of signals will negatively impact its
     *    Publisher´s responsivity, it is RECOMMENDED that it asynchronously dispatches its signals.
     *
     * 3. Subscriber.onComplete() and Subscriber.onError(Throwable t) MUST NOT call any methods on
     *    the Subscription or the Publisher.
     *
     * 4. Subscriber.onComplete() and Subscriber.onError(Throwable t) MUST consider the Subscription
     *    cancelled after having received the signal.
     *
     * 5. A Subscriber MUST call Subscription.cancel() on the given Subscription after an onSubscribe
     *    signal if it already has an active Subscription.
     *
     * 6. A Subscriber MUST call Subscription.cancel() if the Subscription is no longer needed.
     *
     * 7. A Subscriber MUST ensure that all calls on its Subscription take place from the same thread
     *    or provide for respective external synchronization.
     *
     * 8. A Subscriber MUST be prepared to receive one or more onNext signals after having called
     *    Subscription.cancel() if there are still requested elements pending [see 3.12].
     *    Subscription.cancel() does not guarantee to perform the underlying cleaning operations
     *    immediately.
     *
     * 9. A Subscriber MUST be prepared to receive an onComplete signal with or without a preceding
     *    Subscription.request(long n) call.
     *
     * 10. A Subscriber MUST be prepared to receive an onError signal with or without a preceding
     *     Subscription.request(long n) call.
     *
     * 11. A Subscriber MUST make sure that all calls on its signal methods happen-before the processing
     *     of the respective signals. I.e. the Subscriber must take care of properly publishing the signal
     *     to its processing logic.
     *
     * 12. Subscriber.onSubscribe MUST be called at most once for a given Subscriber (based on object
     *     equality).
     *
     *     The intent of this rule is to establish that it MUST be assumed that the same Subscriber
     *     can only be subscribed at most once. Note that object equality is a.equals(b).
     *
     * 13. Calling onSubscribe, onNext, onError or onComplete MUST return normally except when any
     *     provided parameter is null in which case it MUST throw a java.lang.NullPointerException
     *     to the caller, for all other situations the only legal way for a Subscriber to signal
     *     failure is by cancelling its Subscription. In the case that this rule is violated, any
     *     associated Subscription to the Subscriber MUST be considered as cancelled, and the caller
     *     MUST raise this error condition in a fashion that is adequate for the runtime environment.
     *
     */

    @Override
    public void subscribe(Subscriber<? super Buffer> subscriber) {

    }

    @Override
    public void onSubscribe(Subscription subscription) {

    }

    @Override
    public void onNext(Buffer buffer) {

    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {

    }
}
