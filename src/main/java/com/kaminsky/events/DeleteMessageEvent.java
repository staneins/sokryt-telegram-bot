package com.kaminsky.events;

import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;

public class DeleteMessageEvent {
        private final DeleteMessage deleteMessage;

        public DeleteMessageEvent(DeleteMessage deleteMessage) {
            this.deleteMessage = deleteMessage;
        }

        public DeleteMessage getDeleteMessage() {
            return deleteMessage;
        }
}
