package com.kaminsky.service;

import com.kaminsky.model.BotMessage;
import com.kaminsky.model.repositories.BotMessageRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
public class MessageService {

    private final TelegramBot telegramBot;
    private final Map<Long, List<Message>> userMessages = new ConcurrentHashMap<>();
    private final BotMessageRepository botMessageRepository;

    @Autowired
    public MessageService(TelegramBot telegramBot, ChatAdminService chatAdminService, BotMessageRepository botMessageRepository) {
        this.telegramBot = telegramBot;
        this.botMessageRepository = botMessageRepository;
    }

    public void startCommandReceived(Long chatId, String name) {
        String answer = EmojiParser.parseToUnicode("Доброго здоровья, " + name + "!" + " :smiley:");
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(answer);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("петиция о разбане");
        row.add("сайт проекта");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("FAQ о Единоверии");
        row.add("вступить в чат");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
        log.info("Поприветствовали " + name);
    }
    
    public void sendConfigOptions(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Что вы хотите настроить?");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        InlineKeyboardButton welcomeTextButton = new InlineKeyboardButton();
        InlineKeyboardButton recurrentTextButton = new InlineKeyboardButton();

        welcomeTextButton.setCallbackData(telegramBot.getWelcomeTextButton() + ":" + chatId);
        welcomeTextButton.setText("Приветствие");

        recurrentTextButton.setCallbackData(telegramBot.getRecurrentTextButton() + ":" + chatId);
        recurrentTextButton.setText("Автосообщение");

        rowInLine.add(welcomeTextButton);
        rowInLine.add(recurrentTextButton);
        rows.add(rowInLine);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        executeMessage(message);
        log.info("Отправлены опции конфигурации в чат " + chatId);
    }

    public void executeEditMessage(EditMessageText editMessageText) {
        try {
            telegramBot.execute(editMessageText);
        } catch (TelegramApiException e) {
            log.error(telegramBot.getError() + e.getMessage());
        }
    }

    public void executeBanChatMember(BanChatMember banChatMember) {
        try {
            telegramBot.execute(banChatMember);
        } catch (TelegramApiException e) {
            log.error(telegramBot.getError() + e.getMessage());
        }
    }

    public void executeDeleteMessage(DeleteMessage deleteMessage) {
        try {
            telegramBot.execute(deleteMessage);
        } catch (TelegramApiException e) {
            log.error(telegramBot.getError() + e.getMessage());
        }
    }

    public void executeForwardMessage(ForwardMessage forwardMessage) {
        try {
            telegramBot.execute(forwardMessage);
        } catch (TelegramApiException e) {
            log.error(telegramBot.getError() + e.getMessage());
        }
    }

    public void sendMessage(Long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        executeMessage(message);
        log.info("Отправлено сообщение в чат " + chatId + ": " + textToSend);
    }

    public void sendMessage(Long chatId, String textToSend, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        executeMessage(message);
        log.info("Отправлено HTML-сообщение в чат " + chatId + " в ответ на сообщение " + replyToMessageId + ": " + textToSend);
    }

    public void sendHTMLMessage(Long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        message.setParseMode("HTML");
        executeMessage(message);
        log.info("Отправлено HTML-сообщение в чат " + chatId + ": " + textToSend);
    }

    public void sendHTMLMessage(Long chatId, String textToSend, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        message.setReplyToMessageId(replyToMessageId);
        message.setParseMode("HTML");
        executeMessage(message);
        log.info("Отправлено HTML-сообщение в чат " + chatId + " в ответ на сообщение " + replyToMessageId + ": " + textToSend);
    }

    public void sendMarkdownMessage(Long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(fixMarkdownText(textToSend));
        message.setParseMode("MarkdownV2");
        executeMessage(message);
        log.info("Отправлено MarkdownV2-сообщение в чат " + chatId + ": " + textToSend);
    }

    public void sendMarkdownMessage(Long chatId, String textToSend, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(fixMarkdownText(textToSend));
        message.setReplyToMessageId(replyToMessageId);
        message.setParseMode("MarkdownV2");
        executeMessage(message);
        log.info("Отправлено HTML-сообщение в чат " + chatId + " в ответ на сообщение " + replyToMessageId + ": " + textToSend);
    }

    public String fixMarkdownText(String text) {
        return text
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }

    public List<ChatMember> executeGetChatAdministrators(GetChatAdministrators administrators) {
        try {
            return telegramBot.execute(administrators);
        } catch (TelegramApiException e) {
            log.error(telegramBot.getError() + e.getMessage());
        }
        return null;
    }

    private void executeMessage(SendMessage message) {
        try {
            telegramBot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения: " + e.getMessage());
        }
    }

    private String chooseRandomGifUrl() {
        List<String> urls = List.of(
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExdDg4NWoxZWJ6MGh0MXpkaGdrZHZqcGt3Y3JzZ2d3NnQzcDU0OWdsZyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/NEHJpOX8vWHk9dzEjy/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExcHo4MzV6c3U2Z2Vwd3U0Nm56NGhnNHVkc2Izb2F3OGo5NGlldXYyMSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/5WUsLZLkZlMdO/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExZnVvZnFsM3JwbnYwYjNpZGFpOGpjc2t3bW52c3NnMWZ0cm0wa2FxeCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/jquDWJfPUMCiI/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExMWExM3d6dDl4NXlnYmJoOWlieXU4NWE3dnYxejg3d2I0dnZtYmE1NyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/Fkmgse8OMKn9C/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExeHV0Zno0dnMycjk4c2Z0NzBuOXhmZzkzcXE5YjY0aTVldHljb25jMyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/8nmLYpDYSTnOw/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExYm55Z3V1dDQ2a3hyaHJqdXJsYXVzMzExNTFscGRkNHRiNWFqbHNjZCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/l3q2K5jinAlChoCLS/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExcmJiZXM2dnRyMnN3czdpaDVlMzl0c2Nma3ZnaHI3Z20wa2Nha2NsMSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/JSueytO5O29yM/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExZTRnNzFoM3Bvanhub3hweWo4anQ5b3RuNGYxM3ZybnlwZGZsc2gxYSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/70orEIVDASzXW/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExN3h0MTZ4OHp0OTUwdTUwdGRyeGJoNXlzNzltdzgxdWdoc3E5NXJ0NCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/KeoW2fn76yv66qzpJw/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExcDMwaHZzcjFyZmlqMmhxYXJpMzk2NDY5Z2Qyd3FubjNkazk1d2p3YSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/A3V2IWHMlFD9x9NaPD/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExNXcxanUweXNmaXl3bXliYWVnejJtcmNxNmlnbGpjdGVwOWRobmMyeiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/Zhy0nWRKx6cKkTzxUF/giphy.gif",
                "https://i.giphy.com/media/v1.Y2lkPTc5MGI3NjExZ3l4c2czejBhZ2wxZjd0MTZla3N5NW5kNXV2aXQzMjV4NTI4aTUzNCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/5TwN4gQI35fbl5F0C6/giphy.gif"
        );
        Random random = new Random();
        return urls.get(random.nextInt(urls.size()));
    }

    public void sendRandomGif(Long chatId) {
        String gifUrl = chooseRandomGifUrl();
        SendAnimation sendAnimation = new SendAnimation();
        sendAnimation.setChatId(String.valueOf(chatId));
        sendAnimation.setAnimation(new org.telegram.telegrambots.meta.api.objects.InputFile(gifUrl));

        try {
            telegramBot.execute(sendAnimation);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке GIF: {}", e.getMessage());
        }
    }

    public Map<Long, List<Message>> getUserMessages() {
        return userMessages;
    }

    public void clearUserMessages() {
        userMessages.clear();
    }

    public void deleteUserMessages(Long chatId, Long userId) {
        for (Map.Entry<Long, List<Message>> entry : userMessages.entrySet()) {
            if (entry.getKey().equals(userId)) {
                List<Message> messages = entry.getValue();
                for (Message message : messages) {
                    DeleteMessage deleteMessage = new DeleteMessage();
                    deleteMessage.setChatId(String.valueOf(chatId));
                    deleteMessage.setMessageId(message.getMessageId());
                    try {
                        telegramBot.execute(deleteMessage);
                    } catch (TelegramApiException e) {
                        log.error("Ошибка при удалении сообщения ID {}: {}", message.getMessageId(), e.getMessage());
                    }
                }
                messages.clear();
            }
        }
    }

    public void sendMessage(SendMessage message) {
        try {
            telegramBot.execute(message);
        } catch (TelegramApiException e) {
            log.error(getError() + e.getMessage());
        }
    }

    public String getChatTitle(Long chatId) {
        String chatTitle = "";
        try {
            Chat chat = telegramBot.execute(new GetChat(String.valueOf(chatId)));
            chatTitle = chat.getTitle();
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        return chatTitle;
    }

    public String getNotAnAdminError() {
        return telegramBot.getNotAnAdminError();
    }

    public String getUnknownCommand() {
        return telegramBot.getUnknownCommand();
    }

    public String getError() {
        return telegramBot.getError();
    }

    public String getWelcomeTextButton() {
        return telegramBot.getWelcomeTextButton();
    }

    public String getRecurrentTextButton() {
        return telegramBot.getRecurrentTextButton();
    }

    public String getHelpText() {
        return telegramBot.getHelpText();
    }

}