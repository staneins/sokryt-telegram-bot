package com.kaminsky.events;

import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;

public class RestrictChatMemberEvent {
    private final RestrictChatMember restrictChatMember;

    public RestrictChatMemberEvent(RestrictChatMember restrictChatMember) {
        this.restrictChatMember = restrictChatMember;
    }

    public RestrictChatMember getRestrictChatMember() {
        return restrictChatMember;
    }
}