package com.kaminsky.service;

import com.kaminsky.config.BotConfig;
import com.kaminsky.events.*;
import com.kaminsky.model.ChatInfo;
import com.kaminsky.model.repositories.ChatInfoRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllChatAdministrators;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllPrivateChats;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class TelegramBot extends TelegramLongPollingBot {

    private final CommandHandler commandHandler;
    private final CallbackQueryHandler callbackQueryHandler;
    private final ChatAdminService chatAdminService;
    private final BotConfig config;
    private final CaptchaService captchaService;
    private final ChatInfoRepository chatInfoRepository;
    private final UserService userService;

    @Autowired
    public TelegramBot(BotConfig config,
                       CommandHandler commandHandler,
                       CallbackQueryHandler callbackQueryHandler,
                       ChatAdminService chatAdminService,
                       CaptchaService captchaService,
                       ChatInfoRepository chatInfoRepository, UserService userService) {
        this.config = config;
        this.commandHandler = commandHandler;
        this.callbackQueryHandler = callbackQueryHandler;
        this.chatAdminService = chatAdminService;
        this.captchaService = captchaService;
        this.chatInfoRepository = chatInfoRepository;
        this.userService = userService;
        initializeCommands();

    }

    @PostConstruct
    private void initializeCommands() {
        List<BotCommand> privateChatCommands = new ArrayList<>();
        privateChatCommands.add(new BotCommand("/start", "Запустить бота"));
        privateChatCommands.add(new BotCommand("/help", "Описание работы бота"));
        privateChatCommands.add(new BotCommand("/config", "Настройки"));

        try {
            execute(new SetMyCommands(privateChatCommands, new BotCommandScopeAllPrivateChats(), null));
        } catch (TelegramApiException e) {
            log.error("Ошибка при установке команд для приватных чатов", e);
        }

        List<BotCommand> adminChatCommands = new ArrayList<>();
        adminChatCommands.add(new BotCommand("/ban", "Забанить пользователя"));
        adminChatCommands.add(new BotCommand("/mute", "Замутить пользователя"));
        adminChatCommands.add(new BotCommand("/unmute", "Снять ограничения"));
        adminChatCommands.add(new BotCommand("/warn", "Предупредить пользователя"));
        adminChatCommands.add(new BotCommand("/check", "посмотреть количество предупреждений"));
        adminChatCommands.add(new BotCommand("/reset", "сбросить предупреждения"));
        adminChatCommands.add(new BotCommand("/update", "обновить список администраторов"));
        adminChatCommands.add(new BotCommand("/wipe", "очистить историю сообщений"));

        try {
            execute(new SetMyCommands(adminChatCommands, new BotCommandScopeAllChatAdministrators(), null));
        } catch (TelegramApiException e) {
            log.error("Ошибка при установке команд для администраторов", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().getNewChatMembers() != null && !update.getMessage().getNewChatMembers().isEmpty()) {
            Long chatId = update.getMessage().getChatId();
            if (!isNewChatMemberSokrytBot(update)) {
                captchaService.popupCaptcha(update, chatId);
                userService.collectUserMessage(update.getMessage().getFrom().getId(), update.getMessage());
            }
        }

        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = update.getMessage().getChatId();
            userService.collectAllMessages(chatId, message);
            if (message.getLeftChatMember() != null && !userService.getBannedUsers().contains(message.getLeftChatMember().getId())) {
                commandHandler.sayFarewellToUser(message);
            }
            if (update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat()) {
                registerChatInfo(update);
            }
            commandHandler.handleMessage(update.getMessage());
        }

        if (update.hasCallbackQuery()) {
            callbackQueryHandler.handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private boolean isNewChatMemberSokrytBot(Update update) {
        Long botId = config.getBotId();
        for (org.telegram.telegrambots.meta.api.objects.User newUser : update.getMessage().getNewChatMembers()) {
            if (newUser.getId().equals(botId)) {
                return true;
            }
        }
        return false;
    }

    @Cacheable(value = "chatInfo", key = "#chatId")
    protected Optional<ChatInfo> getChatInfoFromCache(Long chatId) {
        return chatInfoRepository.findById(chatId);
    }

    @CachePut(value = "chatInfo", key = "#chatInfo.chatId")
    protected ChatInfo updateChatInfoCache(ChatInfo chatInfo) {
        return chatInfoRepository.save(chatInfo);
    }

    @CacheEvict(value = "chatInfo", key = "#chatId")
    protected void clearChatInfoCache(Long chatId) {
        log.info("Кэш объектов ChatInfo с chatId {} очищен", chatId);
    }

    private void registerChatInfo(Update update) {
        if (update.getMessage().getChat().getTitle() != null) {
            Long chatId = update.getMessage().getChatId();
            String chatTitle = update.getMessage().getChat().getTitle();

            Optional<ChatInfo> cachedChatInfo = getChatInfoFromCache(chatId);
            boolean needUpdate = false;

            if (cachedChatInfo.isEmpty()) {
                needUpdate = true;
            } else if (!cachedChatInfo.get().getChatTitle().equals(chatTitle)) {
                needUpdate = true;
            }

            if (!needUpdate) {
                log.info("Вернули объект ChatInfo из кэша {} : {}", cachedChatInfo.get().getChatId(), cachedChatInfo.get().getChatTitle());
            }

            if (needUpdate) {
                ChatInfo chatInfo = new ChatInfo();
                chatInfo.setChatTitle(chatTitle);
                chatInfo.setChatId(chatId);
                updateChatInfoCache(chatInfo);
                log.info("Записали новый объект ChatInfo {} : {}", chatInfo.getChatId(), chatInfo.getChatTitle());
            }
        }
    }


    @EventListener
    public void handleSendMessageEvent(SendMessageEvent event) {
        SendMessage message = event.getSendMessage();
        try {
            Message sentMessage = execute(message);
            if (sentMessage != null) {
                Long chatId = sentMessage.getChatId();
                userService.collectAllMessages(chatId, sentMessage);
                log.info("Сообщение отправлено в чат {}", message.getChatId());
            }
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения: {}", e.getMessage());
        }
    }


    @EventListener
    public void handleSendCaptchaMessageEvent(SendCaptchaMessageEvent event) {
        SendMessage message = event.getSendMessage();
        try {
            Message sentMessage = execute(message);
            if (sentMessage != null) {
                Long chatId = sentMessage.getChatId();
                userService.collectAllMessages(chatId, sentMessage);
                log.info("Сообщение-каптча отправлено в чат {}", message.getChatId());
            }
            event.setMessageId(sentMessage.getMessageId());
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения-каптчи: {}", e.getMessage());
        }
    }

    @EventListener
    public void handleEditMessageTextEvent(EditMessageTextEvent event) {
        EditMessageText editMessageText = event.getEditMessageText();
        try {
            Message sentMessage = (Message) execute(editMessageText);
            if (sentMessage != null) {
                Long chatId = sentMessage.getChatId();
                userService.collectAllMessages(chatId, sentMessage);
                log.info("Сообщение отредактировано в чате " + editMessageText.getChatId());
            }
        } catch (TelegramApiException e) {
            log.error("Ошибка при редактировании сообщения: " + e.getMessage());
        }
    }

    @EventListener
    public void handleBanChatMemberEvent(BanChatMemberEvent event) {
        BanChatMember banChatMember = event.getBanChatMember();
        try {
            execute(banChatMember);
            log.info("Пользователь забанен в чате " + banChatMember.getChatId());
        } catch (TelegramApiException e) {
            log.error("Ошибка при бане пользователя: " + e.getMessage());
        }
    }

    @EventListener
    public void handleRestrictChatMemberEvent(RestrictChatMemberEvent event) {
        RestrictChatMember restrictChatMember = event.getRestrictChatMember();
        try {
            execute(restrictChatMember);
            log.info("Пользователь обеззвучен в чате " + restrictChatMember.getChatId());
        } catch (TelegramApiException e) {
            log.error("Ошибка при обеззвучивании пользователя: " + e.getMessage());
        }
    }

    @EventListener
    public void handleDeleteMessageEvent(DeleteMessageEvent event) {
        DeleteMessage deleteMessage = event.getDeleteMessage();
        try {
            execute(deleteMessage);
            log.info("Сообщение ID " + deleteMessage.getMessageId() + " удалено из чата " + deleteMessage.getChatId());
        } catch (TelegramApiException e) {
            log.error("Ошибка при удалении сообщения: " + e.getMessage());
        }
    }

    @EventListener
    public void handleForwardMessageEvent(ForwardMessageEvent event) {
        ForwardMessage forwardMessage = event.getForwardMessage();
        try {
            execute(forwardMessage);
            log.info("Сообщение переслано в чат " + forwardMessage.getChatId());
        } catch (TelegramApiException e) {
            log.error("Ошибка при пересылке сообщения: " + e.getMessage());
        }
    }

    @EventListener
    public void handleSendAnimationEvent(SendAnimationEvent event) {
        SendAnimation sendAnimation = event.getSendAnimation();
        try {
            Message sentMessage = execute(sendAnimation);
            if (sentMessage != null) {
                Long chatId = sentMessage.getChatId();
                userService.collectAllMessages(chatId, sentMessage);
                log.info("GIF отправлен в чат " + sendAnimation.getChatId());
            }
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке GIF: " + e.getMessage());
        }
    }

    @EventListener
    public void handleGetChatAdministratorsEvent(GetChatAdministratorsEvent event) {
        GetChatAdministrators getChatAdministrators = event.getGetChatAdministrators();
        CompletableFuture<List<ChatMember>> future = event.getFuture();
        try {
            List<ChatMember> administrators = execute(getChatAdministrators);
            log.info("Администраторы чата " + getChatAdministrators.getChatId() + ": " + administrators.size());
            future.complete(administrators);
        } catch (TelegramApiException e) {
            log.error("Ошибка при получении администраторов чата: " + e.getMessage());
            future.completeExceptionally(e);
        }
    }

    @EventListener
    public void handleGetChatMemberEvent(GetChatMemberEvent event) {
        GetChatMember getChatMember = event.getGetChatMember();
        CompletableFuture<ChatMember> future = event.getFuture();
        try {
            ChatMember chatMember = execute(getChatMember);
            log.info("Получен пользователь {}", chatMember.toString());
            future.complete(chatMember);
        } catch (TelegramApiException e) {
            log.error("Ошибка при получении пользователя: {}", e.getMessage());
            future.completeExceptionally(e);
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }
}

