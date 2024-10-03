package com.kaminsky.service;

import com.kaminsky.config.BotConfig;
import com.kaminsky.model.KeyWord;
import com.kaminsky.model.KeyWordRepository;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KeyWordRepository adRepository;

    final BotConfig config;

    private Map<Long, Boolean> userCaptchaStatus = new HashMap<>();

    private Map<Long, List<Message>> userMessages = new HashMap<>();

    private Set<Long> bannedUsers = new HashSet<>();

    private Map<Long, List<Long>> chatAdministrators = new HashMap<>();

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static final String ERROR = "Ошибка ";

    static final String UNKNOWN_COMMAND = "Мне незнакома эта команда.";

    static final String HELP_TEXT = "Это бот проекта «Сокрытая Русь» и общества сщмч. Андрея (Ухтомского)\n" +
                                    "Основной функционал бота направлен на администрирование чата и связь с модераторами\n" +
                                    "Вы можете увидеть доступные команды в меню слева";

    static final String CONFIG_TEXT = "Что вы хотите настроить?";

    static final String NOT_ADMIN_ERROR = "Для этого нужны права адмистратора.";

    static final String CONFIRM_BUTTON = "CONFIRM_BUTTON";

    static final String WELCOME_TEXT_BUTTON = "WELCOME_TEXT_BUTTON";

    static final String CONFIG_BUTTON = "CONFIG_BUTTON";

    static String welcomeMessage = "Милости прошу к нашему шалашу";

    static String recurrentMessage = "Милости прошу к нашим тематическим шалашам";

    static Long recurrentChatId = 0L;

    @Autowired
    private KeyWordRepository keyWordRepository;

    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> privateChatCommands = new ArrayList<>();
        privateChatCommands.add(new BotCommand("/start", "запустить бот"));
        privateChatCommands.add(new BotCommand("/help", "описание работы бота"));
        privateChatCommands.add(new BotCommand("/config", "настройки"));

        List<BotCommand> publicChatCommands = new ArrayList<>();
        publicChatCommands.add(new BotCommand("/ban", "забанить пользователя"));
        publicChatCommands.add(new BotCommand("/mute", "обеззвучить пользователя"));
        publicChatCommands.add(new BotCommand("/warn", "предупредить пользователя"));
        publicChatCommands.add(new BotCommand("/check", "посмотреть количество предупреждений"));
        publicChatCommands.add(new BotCommand("/reset", "сбросить предупреждения"));
        publicChatCommands.add(new BotCommand("/wipe", "очистить историю сообщений"));

        try {
            this.execute(new SetMyCommands(privateChatCommands, new BotCommandScopeAllPrivateChats(), null));
            this.execute(new SetMyCommands(publicChatCommands, new BotCommandScopeAllChatAdministrators(), null));
        } catch (TelegramApiException e) {
            log.error("Ошибка при написании списка команд: " + e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }
        String botUsername = getBotUsername();

//        boolean isBotMentioned = update.getMessage().getText().contains("@" + botUsername);

        boolean isReplyToBot = update.getMessage().isReply() &&
                update.getMessage().getReplyToMessage().getFrom().getUserName().equals(botUsername);

        if (update.hasMessage() && update.getMessage().getNewChatMembers() != null && !update.getMessage().getNewChatMembers().isEmpty()) {
            Long chatId = update.getMessage().getChatId();
            popupCaptcha(update, chatId);
        }

        if (update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            recurrentChatId = chatId;
            registerAdministrators(chatId);
            String messageText = "";
            Message message = update.getMessage();
            Long userId = message.getFrom().getId();

            if (message.getLeftChatMember() != null && !bannedUsers.contains(message.getLeftChatMember().getId())) {
                sayFarewellToUser(message);
            }

            userMessages.putIfAbsent(userId, new ArrayList<>());
            userMessages.get(userId).add(message);

            if (message.hasText()) {
                messageText = message.getText();
                List<String> keyWords = getKeyWords();
                if (isContainKeyWords(keyWords, messageText)) {
                    sendRandomGif(message.getChatId());
                    muteUser(message.getChatId(), message.getFrom().getId(), message.getFrom().getFirstName(), message, true);
                }
            }

            boolean isGroupChat = update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat();
            boolean isPrivateChat = update.getMessage().getChat().isUserChat();

            if (isPrivateChat) {
                handlePrivateCommand(chatId, messageText, update);
            }

            if (isGroupChat && (isReplyToBot || update.getMessage().getText().contains("@" + botUsername))) {
                handleGroupChatCommand(chatId, messageText, update);
            }
        }
    }

    private void handlePrivateCommand(long chatId, String messageText, Update update) {
        if (messageText.contains("/send") && config.getOwnerId() == chatId) {
            String textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
            Iterable<User> users = userRepository.findAll();
            for (User user : users) {
                prepareAndSendMessage(user.getChatId(), textToSend);
            }
        } else {
            switch (messageText) {
                case "/start":
                    registerUser(update.getMessage());
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    break;
                case "/help":
                    prepareAndSendMessage(chatId, HELP_TEXT);
                    break;
                case "/config":

                    break;
                default:
                    prepareAndSendMessage(chatId, UNKNOWN_COMMAND);
            }
        }
    }

    private void handleGroupChatCommand(long chatId, String messageText, Update update) {
        Long commandSenderId = update.getMessage().getFrom().getId();
        Long objectId = update.getMessage().getReplyToMessage().getFrom().getId();
        String objectName = update.getMessage().getReplyToMessage().getFrom().getFirstName();
        Message message = update.getMessage();

            switch (messageText) {
                case "/ban@sokrytbot":
                    banUser(chatId, commandSenderId, objectId, objectName, message);
                    log.info("Забанили " + update.getMessage().getFrom().getUserName());
                    break;
                case "/warn@sokrytbot":
                    warnUser(chatId, commandSenderId, objectId, objectName, message);
                    break;
                case "/mute@sokrytbot":
                    muteUser(chatId, objectId, objectName, message);
                    break;
                case "/check@sokrytbot":
                    checkWarns(chatId, objectId, objectName, message);
                    break;
                case "/reset@sokrytbot":
                    resetWarns(chatId, objectId, objectName, message);
                    break;
                case "/wipe@sokrytbot":
                    wipeAllMessages();
                    break;
                default:
                    prepareAndSendMessage(chatId, "Мне незнакома эта команда", update.getMessage().getMessageId());
            }
        }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        log.info("Получен callbackQuery с данными: " + callbackQuery.getData());
        String callbackData = callbackQuery.getData();
        long userId = callbackQuery.getFrom().getId();
        long chatId = callbackQuery.getMessage().getChatId();
        long messageId = callbackQuery.getMessage().getMessageId();
        String userFirstName = callbackQuery.getFrom().getFirstName();
        String userLink = "[" + userFirstName + "](tg://user?id=" + userId + ")";

        String[] callbackDataParts = callbackData.split(":");
        String button = callbackDataParts[0];
        long targetUserId = Long.parseLong(callbackDataParts[1]);

        if (button.equals(CONFIRM_BUTTON) && targetUserId == userId) {
            userCaptchaStatus.put(userId, true);
            try {
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(String.valueOf(chatId));
                editMessage.setMessageId((int) messageId);
                editMessage.setText("Добро пожаловать, " + userLink + "\n" + welcomeMessage);
                editMessage.setParseMode("MarkdownV2");
                execute(editMessage);
            } catch (TelegramApiException e) {
                log.error(ERROR + e.getMessage());
            }
        }
    }

    private void registerUser(Message message) {
        if (message != null && message.getChat() != null) {
            if (userRepository.findById(message.getFrom().getId()).isEmpty()) {
                Long chatId = message.getFrom().getId();
                Chat chat = message.getChat();

                User user = new User();
                user.setChatId(chatId);
                user.setFirstName(chat.getFirstName());
                user.setLastName(chat.getLastName());
                user.setUserName(chat.getUserName());
                user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

                userRepository.save(user);
                log.info("Пользователь сохранен: " + user);
            }
        } else {
            log.warn("Попытка зарегистрировать пользователя с пустым сообщением или чатом");
        }
    }

    public void registerAdministrators(Long chatId) {
        try {
            GetChatAdministrators getChatAdministrators = new GetChatAdministrators();
            List<ChatMember> administrators = execute(getChatAdministrators);
            List<Long> administratorsIds = new ArrayList<>();
            for (ChatMember admin : administrators) {
                administratorsIds.add(admin.getUser().getId());
            }
            chatAdministrators.put(chatId, administratorsIds);
        } catch (TelegramApiException e) {
            log.error(ERROR + e.getMessage());
        }
    }

    public List<Message> getUserMessages(Long userId) {
        return userMessages.getOrDefault(userId, new ArrayList<>());
    }

    private void startCommandReceived(Long chatId, String name) {
        String answer = EmojiParser.parseToUnicode("Доброго здоровья, " + name + "!" + " :smiley:");
        prepareAndSendMessage(chatId, answer);
        log.info("Ответил пользователю " + name);
    }

    private void configCommandReceived(Long chatId, Long userId) {







        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        InlineKeyboardButton welcomeTextButton = new InlineKeyboardButton();
        InlineKeyboardButton recurrentTextButton = new InlineKeyboardButton();

        String welcomeAnswer = EmojiParser.parseToUnicode("Приветственное сообщение " + ":wave:");
        welcomeTextButton.setText(welcomeAnswer);
        welcomeTextButton.setCallbackData(WELCOME_TEXT_BUTTON);

        rowInLine.add(confirmButton);
        rows.add(rowInLine);
        markup.setKeyboard(rows);

        message.setReplyMarkup(markup);
    }

    private void banUser(Long chatId, Long commandSenderId, Long bannedUserId, String bannedUserNickname, Message message) {
        try {
            if (isAdmin(chatId, commandSenderId)) {
                if (isAdmin(chatId, bannedUserId)) {
                    prepareAndSendMessage(chatId, "Не могу забанить администратора");
                } else {
                    execute(new BanChatMember(String.valueOf(chatId), bannedUserId));
                    String text = "<a href=\"tg://user?id=" + bannedUserId + "\">" + bannedUserNickname + "</a>" +
                            " уничтожен";
                    prepareAndSendHTMLMessage(chatId, text, message.getMessageId());
                    bannedUsers.add(bannedUserId);
                    cleanUpAndShutDown(1, TimeUnit.MINUTES);
                }
            } else {
                prepareAndSendMessage(chatId, NOT_ADMIN_ERROR);
            }
        } catch (TelegramApiException e) {
            log.error(ERROR + e.getMessage());
        }
    }

    private void warnUser(Long chatId, Long commandSenderId, Long warnedUserId, String warnedUserNickname, Message message) {
        if (isAdmin(chatId, message.getFrom().getId())) {
            User warnedUser = getOrRegisterWarnedUser(message, warnedUserId);
            if (warnedUser != null) {
                Byte warnsCount = warnedUser.getNumberOfWarns();
                if (warnsCount == null) {
                    warnedUser.setNumberOfWarns((byte) 1);

                    userRepository.save(warnedUser);

                    String text = "<a href=\"tg://user?id=" + warnedUserId + "\">" + warnedUserNickname + "</a>" +
                            " предупрежден. \n" + "Количество предупреждений: " + warnedUser.getNumberOfWarns() + " из 3";

                    prepareAndSendHTMLMessage(chatId, text, message.getMessageId());

                } else if (warnsCount == 2) {
                    warnedUser.setNumberOfWarns((byte) 0);

                    userRepository.save(warnedUser);

                    banUser(chatId, commandSenderId, warnedUserId, warnedUserNickname, message);
                } else {
                    warnedUser.setNumberOfWarns((byte) (warnedUser.getNumberOfWarns() + 1));

                    userRepository.save(warnedUser);

                    String text = "<a href=\"tg://user?id=" + warnedUserId + "\">" + warnedUserNickname + "</a>" +
                            " предупрежден. \n" + "Количество предупреждений: " + warnedUser.getNumberOfWarns() + " из 3";

                    prepareAndSendHTMLMessage(chatId, text, message.getMessageId());
                }
            } else {
                prepareAndSendMessage(chatId, ERROR);
            }
        } else {
            prepareAndSendMessage(chatId, NOT_ADMIN_ERROR);
        }
    }

    private void checkWarns(Long chatId, Long warnedUserId, String warnedUserNickname, Message message) {
        if (isAdmin(chatId, message.getFrom().getId())) {
            User warnedUser = getOrRegisterWarnedUser(message, warnedUserId);
            if (warnedUser != null) {
                Byte warnsCount = warnedUser.getNumberOfWarns();

                if (warnsCount == null) {
                    warnedUser.setNumberOfWarns((byte) 0);

                    userRepository.save(warnedUser);
                }
                String text = "Пользователь " + "<a href=\"tg://user?id=" + warnedUserId + "\">" + warnedUserNickname + "</a>" +
                        " Количество предупреждений: " + warnedUser.getNumberOfWarns() + " из 3";

                prepareAndSendHTMLMessage(chatId, text, message.getMessageId());
                }
            } else {
            prepareAndSendMessage(chatId, NOT_ADMIN_ERROR);
        }
    }

    private void resetWarns(Long chatId, Long warnedUserId, String warnedUserNickname, Message message) {
        if (isAdmin(chatId, message.getFrom().getId())) {
            User warnedUser = getOrRegisterWarnedUser(message, warnedUserId);
            if (warnedUser != null) {
                    warnedUser.setNumberOfWarns((byte) 0);
                    userRepository.save(warnedUser);

                String text = "Предупреждения сброшены\n" + "Пользователь "
                        + "<a href=\"tg://user?id=" + warnedUserId + "\">"
                        + warnedUserNickname + "</a>" +
                        "\nКоличество предупреждений: " +
                        warnedUser.getNumberOfWarns() + " из 3";

                prepareAndSendHTMLMessage(chatId, text, message.getMessageId());
            }
        } else {
            prepareAndSendMessage(chatId, NOT_ADMIN_ERROR);
        }
    }

    private void muteUser(Long chatId, Long warnedUserId, String warnedUserNickname, Message message) {
        if (isAdmin(chatId, message.getFrom().getId())) {
            User warnedUser = getOrRegisterWarnedUser(message, warnedUserId);
            if (warnedUser != null) {
                try {
                    Duration muteDuration = Duration.ofDays(1);
                    RestrictChatMember restrictChatMember = new RestrictChatMember(String.valueOf(chatId), warnedUserId, new ChatPermissions());
                    restrictChatMember.forTimePeriodDuration(muteDuration);
                    execute(restrictChatMember);
                    String text = "<a href=\"tg://user?id=" + warnedUserId + "\">" + warnedUserNickname + "</a>" +
                            " обеззвучен на сутки";
                    prepareAndSendHTMLMessage(chatId, text, message.getMessageId());
                } catch (TelegramApiException e) {
                    log.error(ERROR + e.getMessage());
                }
            }
        } else {
            prepareAndSendMessage(chatId, NOT_ADMIN_ERROR);
        }
    }

    private void muteUser(Long chatId, Long warnedUserId, String warnedUserNickname, Message message, Boolean isAdmin) {
        if (isAdmin) {
            User warnedUser = userRepository.findById(warnedUserId).orElse(null);;
            registerUser(message);
            if (warnedUser != null) {
                try {
                    Duration muteDuration = Duration.ofDays(1);
                    RestrictChatMember restrictChatMember = new RestrictChatMember(String.valueOf(chatId), warnedUserId, new ChatPermissions());
                    restrictChatMember.forTimePeriodDuration(muteDuration);
                    execute(restrictChatMember);
                    String text = "<a href=\"tg://user?id=" + warnedUserId + "\">" + warnedUserNickname + "</a>" +
                            " обеззвучен на сутки";
                    prepareAndSendHTMLMessage(chatId, text, message.getMessageId());
                } catch (TelegramApiException e) {
                    log.error(ERROR + e.getMessage());
                }
            }
        } else {
            prepareAndSendMessage(chatId, NOT_ADMIN_ERROR);
        }
    }

    private void wipeAllMessages() {
        if (!userMessages.isEmpty()) {
            for (Map.Entry<Long, List<Message>> entry : userMessages.entrySet()) {
                List<Message> messages = entry.getValue();
                for (Message message : messages) {
                    DeleteMessage deleteMessage = new DeleteMessage();
                    deleteMessage.setMessageId(message.getMessageId());
                    deleteMessage.setChatId(String.valueOf(message.getChatId()));
                    try {
                        execute(deleteMessage);
                        userMessages.clear();
                    } catch (TelegramApiException e) {
                        log.error(ERROR + e.getMessage());
                    }
                }
            }
        }
    }

    private boolean isContainKeyWords(List<String> words, String messageText) {
        String text = messageText.toLowerCase();
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getKeyWords() {
        Iterable<KeyWord> keyWords = keyWordRepository.findAll();
        List<String> words = new ArrayList<>();
        for (KeyWord keyWord : keyWords) {
            words.add(keyWord.getKeyWord());
        }
        return words;
    }

    private void sendRandomGif(long chatId) {
        String gifUrl = chooseRandomGifUrl();
        SendAnimation sendAnimation = new SendAnimation();
        sendAnimation.setChatId(chatId);
        sendAnimation.setAnimation(new InputFile(gifUrl));

        try {
            execute(sendAnimation);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке GIF: " + e.getMessage());
        }
    }

    private String chooseRandomGifUrl() {
        String url1 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExdDg4NWoxZWJ6MGh0MXpkaGdrZHZqcGt3Y3JzZ2d3NnQzcDU0OWdsZyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/NEHJpOX8vWHk9dzEjy/giphy.gif";
        String url2 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExcHo4MzV6c3U2Z2Vwd3U0Nm56NGhnNHVkc2Izb2F3OGo5NGlldXYyMSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/5WUsLZLkZlMdO/giphy.gif";
        String url3 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExZnVvZnFsM3JwbnYwYjNpZGFpOGpjc2t3bW52c3NnMWZ0cm0wa2FxeCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/jquDWJfPUMCiI/giphy.gif";
        String url4 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExMWExM3d6dDl4NXlnYmJoOWlieXU4NWE3dnYxejg3d2I0dnZtYmE1NyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/Fkmgse8OMKn9C/giphy.gif";
        String url5 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExeHV0Zno0dnMycjk4c2Z0NzBuOXhmZzkzcXE5YjY0aTVldHljb25jMyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/8nmLYpDYSTnOw/giphy.gif";
        String url6 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExYm55Z3V1dDQ2a3hyaHJqdXJsYXVzMzExNTFscGRkNHRiNWFqbHNjZCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/l3q2K5jinAlChoCLS/giphy.gif";
        String url7 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExcmJiZXM2dnRyMnN3czdpaDVlMzl0c2Nma3ZnaHI3Z20wa2Nha2NsMSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/JSueytO5O29yM/giphy.gif";
        String url8 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExZTRnNzFoM3Bvanhub3hweWo4anQ5b3RuNGYxM3ZybnlwZGZsc2gxYSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/70orEIVDASzXW/giphy.gif";
        String url9 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExN3h0MTZ4OHp0OTUwdTUwdGRyeGJoNXlzNzltdzgxdWdoc3E5NXJ0NCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/KeoW2fn76yv66qzpJw/giphy.gif";
        String url10 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExcDMwaHZzcjFyZmlqMmhxYXJpMzk2NDY5Z2Qyd3FubjNkazk1d2p3YSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/A3V2IWHMlFD9x9NaPD/giphy.gif";
        String url11 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExNXcxanUweXNmaXl3bXliYWVnejJtcmNxNmlnbGpjdGVwOWRobmMyeiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/Zhy0nWRKx6cKkTzxUF/giphy.gif";
        String url12 = "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExZ3l4c2czejBhZ2wxZjd0MTZla3N5NW5kNXV2aXQzMjV4NTI4aTUzNCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/5TwN4gQI35fbl5F0C6/giphy.gif";
        int randomizer = (int) (Math.random() * 12);

        List<String> urls = new ArrayList<>();
        urls.add(url1);
        urls.add(url2);
        urls.add(url3);
        urls.add(url4);
        urls.add(url5);
        urls.add(url6);
        urls.add(url7);
        urls.add(url8);
        urls.add(url9);
        urls.add(url10);
        urls.add(url11);
        urls.add(url12);

        return urls.get(randomizer);
    }


    private void popupCaptcha(Update update, long chatId) {
        Message msg = update.getMessage();
        if (msg.getNewChatMembers() != null && !msg.getNewChatMembers().isEmpty()) {
            List<org.telegram.telegrambots.meta.api.objects.User> newMembers = msg.getNewChatMembers();
            for (org.telegram.telegrambots.meta.api.objects.User newMember : newMembers) {

                Long userId = newMember.getId();
                String userFirstName = newMember.getFirstName();

                userCaptchaStatus.put(userId, false);

                SendMessage message = new SendMessage();
                String userLink = "<a href=\"tg://user?id=" + userId + "\">" + userFirstName + "</a>" +
                        ", нажмите кнопку в течение 3-х минут, чтобы войти в чат";
                message.setParseMode("HTML");
                message.setChatId(chatId);
                message.setText(userLink);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> rowInLine = new ArrayList<>();
                InlineKeyboardButton confirmButton = new InlineKeyboardButton();

                String answer = EmojiParser.parseToUnicode(":point_right:" + "Я не бот" + ":point_left:");
                confirmButton.setText(answer);
                confirmButton.setCallbackData(CONFIRM_BUTTON + ":" + userId);

                rowInLine.add(confirmButton);
                rows.add(rowInLine);
                markup.setKeyboard(rows);

                message.setReplyMarkup(markup);

                try {
                    Message sentMessage = execute(message);
                    long sentMessageId = sentMessage.getMessageId();

                    scheduleKickTask(chatId, newMember.getId(), sentMessageId);
                } catch (TelegramApiException e) {
                    log.error(ERROR + e.getMessage());
                }
            }
        }
    }

    private void scheduleKickTask(long chatId, long userId, long messageId) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (!userCaptchaStatus.getOrDefault(userId, false)) {
                    try {
                        BanChatMember kickChatMember = new BanChatMember();
                        Duration kickDuration = Duration.ofNanos(1);
                        kickChatMember.forTimePeriodDuration(kickDuration);
                        kickChatMember.setChatId(String.valueOf(chatId));
                        kickChatMember.setUserId(userId);
                        execute(kickChatMember);
                        bannedUsers.add(userId);
                        cleanUpAndShutDown(1, TimeUnit.MINUTES);

                        DeleteMessage deleteMessage = new DeleteMessage();
                        deleteMessage.setChatId(String.valueOf(chatId));
                        deleteMessage.setMessageId((int) messageId);
                        execute(deleteMessage);

                        List<Message> kickedUserMessages = getUserMessages(userId);

                        for (Message kickedUserMessage : kickedUserMessages) {
                            deleteMessage = new DeleteMessage();
                            deleteMessage.setChatId(String.valueOf(chatId));
                            deleteMessage.setMessageId(kickedUserMessage.getMessageId());
                            execute(deleteMessage);
                        }

                    } catch (TelegramApiException e) {
                        log.error(ERROR + e.getMessage());
                    }
                }
            }
        };
        Timer timer = new Timer();
        timer.schedule(task, 30000);
    }

    private void sayFarewellToUser(Message message) {
        org.telegram.telegrambots.meta.api.objects.User leftUser = message.getLeftChatMember();
        long chatId = message.getChatId();

        String userFirstName = leftUser.getFirstName();
        String userLink = "<a href=\"tg://user?id=" + leftUser.getId() + "\">" + userFirstName + "</a>";
        String farewellMessage = "Всего хорошего, " + userLink;

        prepareAndSendHTMLMessage(chatId, farewellMessage, message.getMessageId());
    }

    private void clearBannedUsers() {
        bannedUsers.clear();
        log.info("Список забаненных пользователей очищен");
    }

    private void cleanUpAndShutDown(long interval, TimeUnit timeUnit) {
        startBannedUsersCleanupTask(interval, timeUnit);
        if (bannedUsers.isEmpty()) {
            stopScheduler();
        }
    }

    private void startBannedUsersCleanupTask(long interval, TimeUnit timeUnit) {
        scheduler.scheduleAtFixedRate(() -> {
            clearBannedUsers();
        }, interval, interval, timeUnit);
    }

    public void stopScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    private User getOrRegisterWarnedUser(Message message, Long warnedUserId) {
        registerUser(message.getReplyToMessage());
        return userRepository.findById(warnedUserId).orElse(null);
    }

    private boolean isAdmin(Long chatId, Long userId) {
        try {
            GetChatAdministrators getChatAdministrators = new GetChatAdministrators();
            getChatAdministrators.setChatId(chatId);
            List<ChatMember> administrators = execute(getChatAdministrators);
            return administrators.stream()
                    .anyMatch(admin -> admin.getUser().getId().equals(userId));
        } catch (TelegramApiException e) {
            log.error(ERROR + e.getMessage());
            return false;
        }
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

//    private void executeEditMessageText(String text, long chatId, long messageId) {
//        EditMessageText message = new EditMessageText();
//        message.setChatId(chatId);
//        message.setText(text);
//        message.setMessageId((int) messageId);
//        try {
//            execute(message);
//        } catch (TelegramApiException e) {
//            log.error(ERROR + e.getMessage());
//        }
//    }

    public static void setWelcomeMessage(String welcomeMessage) {
        TelegramBot.welcomeMessage = welcomeMessage;
    }

    public static void setRecurrentMessage(String recurrentMessage) {
        TelegramBot.recurrentMessage = recurrentMessage;
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR + e.getMessage());
        }
    }

    private void prepareAndSendMessage(Long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        executeMessage(message);
    }

    private void prepareAndSendMessage(Long chatId, String textToSend, Integer replyToMessageId){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        message.setReplyToMessageId(replyToMessageId);
        executeMessage(message);
    }

    private void prepareAndSendHTMLMessage(Long chatId, String textToSend, Integer replyToMessageId){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        message.setReplyToMessageId(replyToMessageId);
        message.setParseMode("HTML");
        executeMessage(message);
    }

    private void prepareAndSendHTMLMessage(Long chatId, String textToSend){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        message.setParseMode("HTML");
        executeMessage(message);
    }

    @Scheduled(cron = "${cron.scheduler}")
    private void sendRecurrentMessage() {
        prepareAndSendHTMLMessage(recurrentChatId, recurrentMessage);
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
