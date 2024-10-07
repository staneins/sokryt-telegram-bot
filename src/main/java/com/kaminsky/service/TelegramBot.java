package com.kaminsky.service;

import com.kaminsky.config.BotConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllChatAdministrators;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllPrivateChats;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.util.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final CommandHandler commandHandler;
    private final CallbackQueryHandler callbackQueryHandler;
    private final ChatAdminService chatAdminService;
    private final BotConfig config;
    private Long botId = 0L;

    private final String welcomeTextButton = "WELCOME_TEXT_BUTTON";
    private final String recurrentTextButton = "RECURRENT_TEXT_BUTTON";
    private final String helpText = "Это бот проекта «Сокрытая Русь» и общества сщмч. Андрея (Ухтомского)\n\n" +
            "Основной функционал бота направлен на администрирование чата и связь с модераторами\n\n" +
            "Вы можете увидеть доступные команды в меню слева и снизу";
    private final String error = "Ошибка ";
    private final String unknownCommand = "Мне незнакома эта команда";
    private final String notAnAdminError = "Для этого нужны права администратора";

    @Autowired
    public TelegramBot(BotConfig config,
                       CommandHandler commandHandler,
                       CallbackQueryHandler callbackQueryHandler,
                       ChatAdminService chatAdminService) {
        this.config = config;
        this.commandHandler = commandHandler;
        this.callbackQueryHandler = callbackQueryHandler;
        this.chatAdminService = chatAdminService;
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
        adminChatCommands.add(new BotCommand("/warn", "Предупредить пользователя"));
        adminChatCommands.add(new BotCommand("/check", "посмотреть количество предупреждений"));
        adminChatCommands.add(new BotCommand("/reset", "сбросить предупреждения"));
        adminChatCommands.add(new BotCommand("/wipe", "очистить историю сообщений"));

        try {
            execute(new SetMyCommands(adminChatCommands, new BotCommandScopeAllChatAdministrators(), null));
        } catch (TelegramApiException e) {
            log.error("Ошибка при установке команд для администраторов", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            botId = getMe().getId();
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();
            if (update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat()) {
                chatAdminService.registerAdministrators(chatId);
            }
            commandHandler.handleMessage(update.getMessage());
        }

        if (update.hasCallbackQuery()) {
            callbackQueryHandler.handleCallbackQuery(update.getCallbackQuery());
        }
    }

    public Long getBotId() {
        return botId;
    }

    public String getNotAnAdminError() {
        return notAnAdminError;
    }

    public String getUnknownCommand() {
        return unknownCommand;
    }

    public String getError() {
        return error;
    }

    public String getWelcomeTextButton() {
        return welcomeTextButton;
    }

    public String getRecurrentTextButton() {
        return recurrentTextButton;
    }

    public String getHelpText() {
        return helpText;
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

