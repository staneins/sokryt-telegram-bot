package com.kaminsky.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChatAdminService {

    private final MessageService messageService;
    private final Map<Long, List<Long>> chatAdministrators = new ConcurrentHashMap<>();

    @Autowired
    public ChatAdminService(MessageService messageService) {
        this.messageService = messageService;
    }

    public void registerAdministrators(Long chatId) {
        GetChatAdministrators getChatAdministrators = new GetChatAdministrators();
        getChatAdministrators.setChatId(chatId.toString());

        List<ChatMember> administrators = messageService.executeGetChatAdministrators(getChatAdministrators);
        List<Long> administratorIds = administrators.stream()
                .map(admin -> admin.getUser().getId())
                .collect(Collectors.toList());

        chatAdministrators.put(chatId, administratorIds);
        log.info("Зарегистрированы администраторы чата {}: {}", chatId, administratorIds);

    }

    public boolean isAdmin(Long chatId, Long userId) {
        List<Long> admins = chatAdministrators.get(chatId);
        if (admins == null) {
            registerAdministrators(chatId);
            admins = chatAdministrators.get(chatId);
        }
        return admins != null && admins.contains(userId);
    }

    public Map<Long, List<Long>> getChatAdministrators() {
        return chatAdministrators;
    }
}
