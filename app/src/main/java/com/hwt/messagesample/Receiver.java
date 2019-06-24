package com.hwt.messagesample;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by huangwentao on 2019/6/24
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Receiver {
    Scheduler scheduler() default Scheduler.DEFAULT;
    enum Scheduler {
        MAIN,
        IO,
        NEW_THREAD,
        DEFAULT,
        COMPUTATION,
        TRAMPOLINE
    }
}
