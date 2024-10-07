package com.kaminsky.events;

import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

public class EditMessageTextEvent {
    private final EditMessageText editMessageText;

    public EditMessageTextEvent(EditMessageText editMessageText) {
        this.editMessageText = editMessageText;
    }

    public EditMessageText getEditMessageText() {
        return editMessageText;
    }
}
