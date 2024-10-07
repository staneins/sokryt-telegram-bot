package com.kaminsky.events;

import com.kaminsky.service.MessageService;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;


public class SendCaptchaMessageEvent {
    private final SendMessage sendMessage;
    private Integer messageId;  // Новый атрибут для хранения messageId

    public SendCaptchaMessageEvent(SendMessage sendMessage) {
        this.sendMessage = sendMessage;
    }

    public SendMessage getSendMessage() {
        return sendMessage;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }
}
