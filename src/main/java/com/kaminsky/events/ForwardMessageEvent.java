package com.kaminsky.events;

import org.telegram.telegrambots.meta.api.methods.ForwardMessage;

public class ForwardMessageEvent {
    private final ForwardMessage forwardMessage;

    public ForwardMessageEvent(ForwardMessage forwardMessage) {
        this.forwardMessage = forwardMessage;
    }

    public ForwardMessage getForwardMessage() {
        return forwardMessage;
    }
}
