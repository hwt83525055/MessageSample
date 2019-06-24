package com.hwt.messagesample;

import java.util.UUID;

/**
 * Created by huangwentao on 2019/6/24
 */
public class Message {
    private boolean isSticky = false;

    private final UUID mUUID = UUID.randomUUID();

    public boolean isSticky() {
        return isSticky;
    }

    public void setSticky(boolean sticky) {
        isSticky = sticky;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Message) {
            return mUUID.equals(((Message) obj).mUUID);
        }
        return super.equals(obj);
    }
}
