package com.kaminsky.events;

import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GetChatAdministratorsEvent {
    private final GetChatAdministrators getChatAdministrators;
    private final CompletableFuture<List<ChatMember>> future;

    public GetChatAdministratorsEvent(GetChatAdministrators getChatAdministrators, CompletableFuture<List<ChatMember>> future) {
        this.getChatAdministrators = getChatAdministrators;
        this.future = future;
    }

    public GetChatAdministrators getGetChatAdministrators() {
        return getChatAdministrators;
    }

    public CompletableFuture<List<ChatMember>> getFuture() {
        return future;
    }
}
