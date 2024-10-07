package com.kaminsky.events;

import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;

public class GetChatAdministratorsEvent {
    private final GetChatAdministrators getChatAdministrators;

    public GetChatAdministratorsEvent(GetChatAdministrators getChatAdministrators) {
        this.getChatAdministrators = getChatAdministrators;
    }

    public GetChatAdministrators getGetChatAdministrators() {
        return getChatAdministrators;
    }
}
