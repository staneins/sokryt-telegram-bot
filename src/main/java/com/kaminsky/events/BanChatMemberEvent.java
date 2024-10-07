package com.kaminsky.events;

import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;

public class BanChatMemberEvent {
    private final BanChatMember banChatMember;

    public BanChatMemberEvent(BanChatMember banChatMember) {
        this.banChatMember = banChatMember;
    }

    public BanChatMember getBanChatMember() {
        return banChatMember;
    }
}
