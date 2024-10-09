package com.kaminsky.events;

import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;

import java.util.concurrent.CompletableFuture;

public class GetChatMemberEvent {
    private final GetChatMember getChatMember;
    private final CompletableFuture<ChatMember> future;

    public GetChatMemberEvent(GetChatMember getChatMember, CompletableFuture<ChatMember> future) {
        this.getChatMember = getChatMember;
        this.future = future;
    }

    public GetChatMember getGetChatMember() {
        return getChatMember;
    }

    public CompletableFuture<ChatMember> getFuture() {
        return future;
    }
}
