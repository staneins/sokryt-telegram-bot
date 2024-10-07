package com.kaminsky.events;

import org.springframework.context.ApplicationEvent;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;


public class SendMessageEvent {
    private final SendMessage sendMessage;

    public SendMessageEvent(SendMessage sendMessage) {
        this.sendMessage = sendMessage;
    }

    public SendMessage getSendMessage() {
        return sendMessage;
    }

}
