package com.kaminsky.events;

import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;

public class SendAnimationEvent {
    private final SendAnimation sendAnimation;

    public SendAnimationEvent(SendAnimation sendAnimation) {
        this.sendAnimation = sendAnimation;
    }

    public SendAnimation getSendAnimation() {
        return sendAnimation;
    }
}
