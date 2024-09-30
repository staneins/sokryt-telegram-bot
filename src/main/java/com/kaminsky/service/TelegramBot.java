package com.kaminsky.service;

import com.kaminsky.config.BotConfig;
import com.kaminsky.model.Ad;
import com.kaminsky.model.AdRepository;
import com.kaminsky.model.User;
import com.kaminsky.model.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllGroupChats;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdRepository adRepository;

    final BotConfig config;

    static final String HELP_TEXT = "Этот бот пока ничего не умеет, кроме приветствий.\n" +
                                    "Вы можете увидеть список будущих команд в меню слева.";

    static final String YES_BUTTON = "YES_BUTTON";
    static final String NO_BUTTON = "NO_BUTTON";

    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> privateChatCommands = new ArrayList<>();
        privateChatCommands.add(new BotCommand("/start", "запустить бот"));
        privateChatCommands.add(new BotCommand("/mydata", "данные о пользователе"));
        privateChatCommands.add(new BotCommand("/deletedata", "удалить данные о пользователе"));
        privateChatCommands.add(new BotCommand("/help", "описание работы бота"));
        privateChatCommands.add(new BotCommand("/settings", "установить настройки"));

        List<BotCommand> publicChatCommands = new ArrayList<>();
        publicChatCommands.add(new BotCommand("/ban@sokrytbot", "забанить пользователя"));
        publicChatCommands.add(new BotCommand("/mute@sokrytbot", "обеззвучить пользователя"));
        publicChatCommands.add(new BotCommand("/warn@sokrytbot", "предупредить пользователя"));

        try {
            this.execute(new SetMyCommands(privateChatCommands, new BotCommandScopeDefault(), null));
            this.execute(new SetMyCommands(publicChatCommands, new BotCommandScopeAllGroupChats(), null));
        } catch (TelegramApiException e) {
            log.error("Ошибка при написании списка команд: " + e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        String botUsername;

        try {
            botUsername = getMe().getUserName();
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

        boolean isBotMentioned = update.getMessage().getText().contains("@" + botUsername);

        boolean isReplyToBot = update.getMessage().isReply() &&
                update.getMessage().getReplyToMessage().getFrom().getUserName().equals(botUsername);

        if (update.hasMessage() && update.getMessage().hasText() && (isReplyToBot || update.getMessage().getText().contains("@" + botUsername))) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.contains("/send@sokrytbot") && config.getOwnerId() == chatId) {
                String textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                Iterable<User> users = userRepository.findAll();
                for (User user : users) {
                    prepareAndSendMessage(user.getChatId(), textToSend);
                }
            } else {

            switch (messageText) {
                case "/start@sokrytbot":
                    registerUser(update.getMessage());
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    break;
                case "/help@sokrytbot":
                    prepareAndSendMessage(chatId, HELP_TEXT);
                    break;
                case "/register@sokrytbot":
                    register(chatId);
                    break;
                case "/ban@sokrytbot":
                    banUser(update.getMessage().getChatId(), update.getMessage().getReplyToMessage().getFrom().getId(), update.getMessage().getReplyToMessage().getFrom().getFirstName());
                    log.info("Забанили " + update.getMessage().getFrom().getUserName());
                    break;
                case "/warn@sokrytbot":

                    break;
                default:
                    prepareAndSendMessage(chatId, "Мне незнакома эта команда");
            }
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals(YES_BUTTON)) {
                String text = "Вы нажали кнопку ДА";
                executeEditMessageText(text, chatId, messageId);

            } else if (callbackData.equals(NO_BUTTON)) {
                String text = "Вы нажали кнопку НЕТ";
                executeEditMessageText(text, chatId, messageId);
            }
        }
    }

    private void register(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Вы действительно хотите зарегистрироваться?");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        InlineKeyboardButton yesButton = new InlineKeyboardButton();

        yesButton.setText("Да");
        yesButton.setCallbackData(YES_BUTTON);

        InlineKeyboardButton noButton = new InlineKeyboardButton();

        noButton.setText("Нет");
        noButton.setCallbackData(NO_BUTTON);

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rows.add(rowInLine);

        markup.setKeyboard(rows);

        message.setReplyMarkup(markup);

        executeMessage(message);

    }

    private void registerUser(Message message) {
        if (userRepository.findById(message.getChatId()).isEmpty()) {
            Long chatId = message.getChatId();
            Chat chat = message.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("пользователь сохранен: " + user);
        }
    }

    private void startCommandReceived(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode("Доброго здоровья, " + name + "!" + " :smiley:");
        prepareAndSendMessage(chatId, answer);
        log.info("Ответил пользователю " + name);
    }

    private void banUser(long chatId, long bannedUserId, String bannedUserNickname) {
        try {
            GetChatAdministrators getChatAdministrators = new GetChatAdministrators();
            getChatAdministrators.setChatId(chatId);
            List<ChatMember> administrators = execute(getChatAdministrators);

            boolean isAdmin = administrators.stream()
                    .anyMatch(admin -> admin.getUser().getId().equals(bannedUserId));
            if (isAdmin) {
                prepareAndSendMessage(chatId, "Не могу забанить администратора");
            } else {
                execute(new BanChatMember(String.valueOf(chatId), bannedUserId));
                String text = bannedUserNickname + " уничтожен.";
                prepareAndSendMessage(chatId, text);
            }
        } catch (TelegramApiException e) {
            log.error("Ошибка: " + e.getMessage());
        }
    }

    private void warnUser() {

    }

    private void muteUser() {

    }

//    private void keyboardMethod(long chatId, String textToSend) {
//
//        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
//        List<KeyboardRow> keyboardRows = new ArrayList<>();
//        KeyboardRow row = new KeyboardRow();
//
//        row.add("петиция о разбане");
//        row.add("сайт проекта");
//
//        keyboardRows.add(row);
//
//        row = new KeyboardRow();
//
//        row.add("FAQ о Единоверии");
//        row.add("карта приходов");
//        row.add("обучение");
//
//        keyboardRows.add(row);
//
//        keyboardMarkup.setKeyboard(keyboardRows);
//
//        message.setReplyMarkup(keyboardMarkup);
//
//        executeMessage(message);
//    }

    private void executeEditMessageText(String text, long chatId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId((int) messageId);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка: " + e.getMessage());
        }
    }

    private void executeEditMessageText(String text, long chatId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка: " + e.getMessage());
        }
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка: " + e.getMessage());
        }
    }

    private void prepareAndSendMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        executeMessage(message);
    }

    @Scheduled(cron = "${cron.scheduler}")
    private void sendAd(){
        Iterable<Ad> ads = adRepository.findAll();
        Iterable<User> users = userRepository.findAll();

        for (Ad ad : ads) {
            for (User user : users) {
                prepareAndSendMessage(user.getChatId(), ad.getAd());
            }
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
