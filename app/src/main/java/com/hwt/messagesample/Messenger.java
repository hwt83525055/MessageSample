package com.hwt.messagesample;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.Subject;

import static com.hwt.messagesample.Receiver.Scheduler.COMPUTATION;
import static com.hwt.messagesample.Receiver.Scheduler.MAIN;
import static com.hwt.messagesample.Receiver.Scheduler.NEW_THREAD;
import static com.hwt.messagesample.Receiver.Scheduler.TRAMPOLINE;
import static com.hwt.messagesample.Receiver.Scheduler.IO;

/**
 * Created by huangwentao on 2019/6/24
 */
public class Messenger {
    private static final Subject<Message> messenger = StickySubject.create();

    public static void send(Message message) {
        messenger.onNext(message);
    }

    private static final Map<String, CompositeDisposable> subscriverDisposableMap = new HashMap<>();

    public static void subscribe(Object subscriber, Class subscriberClass) {
        String proxyName = subscriberClass.getName() + "_MessengerProxy";
        try {
            Class proxyClass = Class.forName(proxyName);
            final Object proxy = proxyClass.newInstance();
            Method[] methods = proxyClass.getDeclaredMethods();
            for (Method method : methods) {
                if (!method.getName().contains("__")) {
                    continue;
                }
                Receiver receiver = new Receiver();
                receiver.origin = new WeakReference<>(subscriber);
                receiver.proxy = proxy;
                receiver.originClass = subscriberClass;
                receiver.method = method;
                String[] components = method.getName().split("__");
                receiver.scheduler = components[1];
                receiver.messageType = method.getParameterTypes()[0];
                subscribeInternal(receiver);
            }
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ignore) {
        }
    }

    private static void subscribeInternal(final Receiver receiver) {
        Disposable disposable = changeScheduler(receiver.scheduler)
                .subscribe(new StickySubject.MessageConsumer((StickySubject) messenger) {
                    @Override
                    public boolean acceptn(Object o) throws Exception {
                        if (receiver.origin == null || receiver.origin.get() == null) {
                            return false;
                        }
                        CompositeDisposable s = subscriverDisposableMap
                                .get(getKey(receiver.origin.get(), receiver.originClass));
                        if (s == null || s.isDisposed()) {
                            return false;
                        }
                        if (o.getClass().equals(receiver.messageType)) {
                            // 消息, 原订阅者对象
                            receiver.method.invoke(receiver.proxy, o, receiver.origin.get());
                            return true;
                        }
                        return false;
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                    }
                });
        addDisposable(receiver, disposable);
    }

    private static Observable<Message> changeScheduler(String schedulerString) {
        if (schedulerString.equals(IO.toString())) {
            return messenger.observeOn(Schedulers.io());
        } else if (schedulerString.equals(MAIN.name())) {
            return messenger.observeOn(AndroidSchedulers.mainThread());
        } else if (schedulerString.equals(NEW_THREAD.name())) {
            return messenger.observeOn(Schedulers.newThread());
        } else if (schedulerString.equals(COMPUTATION.name())) {
            return messenger.observeOn(Schedulers.computation());
        } else if (schedulerString.equals(TRAMPOLINE.name())) {
            return messenger.observeOn(Schedulers.trampoline());
        } else {
            return messenger;
        }
    }

    private static void addDisposable(Receiver receiver, Disposable disposable) {
        CompositeDisposable compositeDisposable = subscriverDisposableMap
                .get(getKey(receiver.origin.get(), receiver.originClass));
        if (compositeDisposable == null) {
            compositeDisposable = new CompositeDisposable();
            subscriverDisposableMap.put(getKey(receiver.origin.get(), receiver.originClass)
                    , compositeDisposable);
        }
        compositeDisposable.add(disposable);
    }

    public static void unSubscribe(Object subscriber, Class subscriberClass) {
        CompositeDisposable s = subscriverDisposableMap.get(getKey(subscriber, subscriberClass));
        if (s != null && !s.isDisposed()) {
            s.dispose();
        }
        subscriverDisposableMap.remove(getKey(subscriber, subscriberClass));
    }

    private static String getKey(Object subscriber, Class subscriberClass) {
        if (subscriber == null) {
            return null;
        }
        return subscriber.hashCode() + subscriberClass.getName();
    }

    private static class Receiver {
        WeakReference<Object> origin;
        Class originClass;
        Object proxy;
        Method method;
        /**
         * 订阅者消息接收的执行线程
         */
        String scheduler;
        Class messageType;
    }
}
