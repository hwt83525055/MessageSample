package com.hwt.messagesample;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subjects.Subject;

/**
 * Created by huangwentao on 2019/6/24
 */
public final class StickySubject<T extends Message> extends Subject<T> {

    private final AtomicReference<Message[]> stickyMsg;

    private final AtomicReference<StickyDisposable<T>[]> subscribers;

    private static final StickyDisposable[] EMPTY = new StickyDisposable[0];

    private static final StickyDisposable[] TERMINATED = new StickyDisposable[0];

    public static <T extends Message> StickySubject<T> create() {
        return new StickySubject<T>();
    }

    private StickySubject() {
        this.stickyMsg = new AtomicReference<>(new Message[0]);
        this.subscribers = new AtomicReference<StickyDisposable<T>[]>(EMPTY);
    }

    private boolean add(StickyDisposable<T> disposable) {
        for (;;) {
            StickyDisposable[] subscribers = this.subscribers.get();
            if (subscribers == TERMINATED) {
                return false;
            }
            int length = subscribers.length;
            StickyDisposable[] tmp = new StickyDisposable[length + 1];
            System.arraycopy(subscribers, 0, tmp, 0, length);
            tmp[length] = disposable;
            if (this.subscribers.compareAndSet(subscribers, tmp)) {
                offerMsg(tmp);
                return true;
            }
        }
    }

    private void offerMsg(StickyDisposable[] tmp) {
        Message[] msg = stickyMsg.get();
        for (StickyDisposable disposable : tmp) {
            disposable.onNext(msg);
        }
    }

    private synchronized boolean consume(Message msg) {
        for (;;) {
            StickyDisposable[] disposables = this.subscribers.get();
            if (disposables == TERMINATED) {
                return false;
            }
            Message[] msgCache = this.stickyMsg.get();
            int position = 0;
            for (;;) {
                if (position >= msgCache.length) {
                    return false;
                }
                if (msgCache[position].equals(msg)) {
                    break;
                }
                position ++;
            }
            Message[] after = new Message[msgCache.length - 1];
            if (msgCache.length > 1) {
                System.arraycopy(msgCache, 0, after, 0, position);
                System.arraycopy(msgCache, position + 1, after, position, msgCache.length - position - 1);
            }
            if (this.stickyMsg.compareAndSet(msgCache, after)) {
                return true;
            }
        }
    }

    private synchronized boolean hasConsume(Message msg) {
        Message[] msgs = this.stickyMsg.get();
        for (Message sMsg : msgs) {
            if (sMsg.equals(msg)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasObservers() {
        return this.subscribers.get().length > 0;
    }

    @Override
    public boolean hasThrowable() {
        return false;
    }

    @Override
    public boolean hasComplete() {
        return this.subscribers.get() == TERMINATED;
    }

    @Override
    public Throwable getThrowable() {
        return null;
    }

    @Override
    protected void subscribeActual(Observer<? super T> observer) {
        StickyDisposable<T> disposable = new StickyDisposable<T>(observer, this);
        observer.onSubscribe(disposable);
        if (add(disposable)) {
            if (disposable.isDisposed()) {
                remove(disposable);
            }
        } else {
            observer.onComplete();
        }
    }

    @Override
    public void onSubscribe(Disposable d) {

    }

    @Override
    public synchronized void onNext(T t) {
        StickyDisposable[] stickyDisposables = this.subscribers.get();
        if (t.isSticky()) {
            for (;;) {
                Message[] msgCache = this.stickyMsg.get();
                Message[] tempCache = new Message[msgCache.length + 1];
                System.arraycopy(msgCache, 0, tempCache, 0, msgCache.length);
                tempCache[msgCache.length] = t;
                if (stickyMsg.compareAndSet(msgCache, tempCache)) {
                    break;
                }
            }
        }
        for (StickyDisposable disposable : stickyDisposables) {
            disposable.onNext(t);
        }
    }

    @Override
    public void onError(Throwable e) {
        RxJavaPlugins.onError(e);
    }

    @Override
    public void onComplete() {
        StickyDisposable[] disposables = this.subscribers.get();
        for (StickyDisposable disposable : disposables) {
            disposable.onComplete();
        }
        this.subscribers.set(TERMINATED);
    }

    public void remove(StickyDisposable<T> disposable) {
        for (;;) {
            StickyDisposable[] disposables = this.subscribers.get();
            if (disposables == EMPTY || disposables == TERMINATED) {
                return;
            }
            int position = 0;
            while (position <= disposables.length - 1 && disposable != disposables[position]) {
                position++;
            }
            if (position > disposables.length - 1){
                return;
            }
            StickyDisposable[] temp = new StickyDisposable[disposables.length - 1];
            if (disposables.length == 1) {
                temp = EMPTY;
            } else {
                System.arraycopy(disposables, 0, temp, 0, position);
                System.arraycopy(disposables, position + 1, temp, position, disposables.length - 1 - position);
            }
            if (this.subscribers.compareAndSet(disposables, temp)) {
                return;
            }
        }
    }

    static final class StickyDisposable<T extends Message> extends AtomicBoolean implements Disposable {

        final Observer<? super T> actual;

        final StickySubject<T> parent;

        StickyDisposable(Observer<? super T> actual, StickySubject<T> parent) {
            super(true);
            this.actual = actual;
            this.parent = parent;
        }

        void onNext(T t) {
            if (get()) {
                this.actual.onNext(t);
            }
        }

        void onNext(T[] t) {
            if (get()) {
                for (T s : t) {
                    this.actual.onNext(s);
                }
            }
        }

        void onComplete() {
            if (get()) {
                this.actual.onComplete();
            }
        }

        void onError(Throwable t) {
            if (get()) {
                this.actual.onError(t);
            } else {
                RxJavaPlugins.onError(t);
            }
        }

        @Override
        public void dispose() {
            if (compareAndSet(true, false)) {
                parent.remove(this);
            }
        }

        @Override
        public boolean isDisposed() {
            return !get();
        }
    }

    public abstract static class MessageConsumer implements Consumer<Message> {

        private final StickySubject mSubject;

        public MessageConsumer(StickySubject subject) {
            this.mSubject = subject;
        }

        @Override
        public void accept(Message msg) throws Exception {
            if (msg.isSticky() && !this.mSubject.hasConsume(msg)
                    && acceptn(msg)) {
                this.mSubject.consume(msg);
                return;
            }
            acceptn(msg);
        }

        abstract boolean acceptn(Object o) throws Exception;

    }
}

