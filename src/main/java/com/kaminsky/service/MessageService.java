package com.kaminsky.service;

import com.kaminsky.events.*;
import com.kaminsky.finals.BotFinalVariables;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;


@Slf4j
@Service
public class MessageService {

    private final Map<Long, List<Message>> userMessages = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public MessageService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
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
        log.info("Поприветствовали {}", name);
    }
    
    public void sendConfigOptions(Long chatId, Long targetChatId, Integer prevMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Что вы хотите настроить?");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        InlineKeyboardButton welcomeTextButton = new InlineKeyboardButton();
        InlineKeyboardButton recurrentTextButton = new InlineKeyboardButton();

        welcomeTextButton.setCallbackData(BotFinalVariables.WELCOME_TEXT_BUTTON + ":" + targetChatId);
        welcomeTextButton.setText("Приветствие");

        recurrentTextButton.setCallbackData(BotFinalVariables.RECURRENT_TEXT_BUTTON + ":" + targetChatId);
        recurrentTextButton.setText("Автосообщение");

        rowInLine.add(welcomeTextButton);
        rowInLine.add(recurrentTextButton);
        rows.add(rowInLine);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        executeMessage(message);
        log.info("Отправлены опции конфигурации в чат {}", chatId);

        executeDeleteMessage(new DeleteMessage(
                String.valueOf(chatId), prevMessageId));
    }

    public void sayFarewellToUser(Long chatId, Long userId, String userFirstName, Integer messageId) {
        String userLink = "<a href=\"tg://user?id=" + userId + "\">" + userFirstName + "</a>";
        String farewellMessage = "Всего хорошего, " + userLink;

        sendHTMLMessage(chatId, farewellMessage, messageId);
    }

    public void executeEditMessage(EditMessageText editMessageText) {
        eventPublisher.publishEvent(new EditMessageTextEvent(editMessageText));
        log.info("Публикация события EditMessageTextEvent");
    }

    public void executeBanChatMember(BanChatMember banChatMember) {
        eventPublisher.publishEvent(new BanChatMemberEvent(banChatMember));
        log.info("Публикация события BanChatMemberEvent");
    }

    public void executeRestrictChatMember(RestrictChatMember restrictChatMember) {
        eventPublisher.publishEvent(new RestrictChatMemberEvent(restrictChatMember));
        log.info("Публикация события RestrictChatMemberEvent");
    }

    public void executeDeleteMessage(DeleteMessage deleteMessage) {
        eventPublisher.publishEvent(new DeleteMessageEvent(deleteMessage));
        log.info("Публикация события DeleteMessageEvent");
    }

    public void executeForwardMessage(ForwardMessage forwardMessage) {
        eventPublisher.publishEvent(new ForwardMessageEvent(forwardMessage));
        log.info("Публикация события ForwardMessageEvent");
    }

    public void sendMessage(Long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        eventPublisher.publishEvent(new SendMessageEvent(message));
        log.info("Публикация события SendMessageEvent для чата {}", chatId);
    }

    public void sendMessage(Long chatId, String textToSend, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        message.setReplyToMessageId(replyToMessageId);
        eventPublisher.publishEvent(new SendMessageEvent(message));
        log.info("Публикация события SendMessageEvent с ответом на сообщение для чата {}", chatId);
    }

    public void sendHTMLMessage(Long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        message.setParseMode("HTML");
        eventPublisher.publishEvent(new SendMessageEvent(message));
        log.info("Публикация события SendMessageEvent (HTML) для чата {}", chatId);
    }

    public void sendHTMLMessage(Long chatId, String textToSend, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        message.setReplyToMessageId(replyToMessageId);
        message.setParseMode("HTML");
        eventPublisher.publishEvent(new SendMessageEvent(message));
        log.info("Публикация события SendMessageEvent (HTML) с ответом на сообщение для чата {}", chatId);
    }

    public void sendHTMLMessageWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboardMarkup, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("HTML");
        message.setReplyToMessageId(replyToMessageId);
        message.setReplyMarkup(keyboardMarkup);

        eventPublisher.publishEvent(new SendMessageEvent(message));
        log.info("Публикация события SendMessageEvent (HTML) с клавиатурой для чата {}", chatId);
    }



    public void sendMarkdownMessage(Long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        message.setParseMode("MarkdownV2");
        eventPublisher.publishEvent(new SendMessageEvent(message));
        log.info("Публикация события SendMessageEvent (MarkdownV2) для чата {}", chatId);
    }

    public void sendMarkdownMessage(Long chatId, String textToSend, Integer replyToMessageId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textToSend);
        message.setReplyToMessageId(replyToMessageId);
        message.setParseMode("MarkdownV2");
        eventPublisher.publishEvent(new SendMessageEvent(message));
        log.info("Публикация события SendMessageEvent (MarkdownV2) с ответом на сообщение для чата {}", chatId);
    }

    public List<ChatMember> executeGetChatAdministrators(GetChatAdministrators getChatAdministrators) {
        CompletableFuture<List<ChatMember>> future = new CompletableFuture<>();
        eventPublisher.publishEvent(new GetChatAdministratorsEvent(getChatAdministrators, future));
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Ошибка при получении администраторов чата: {}", e.getMessage());
            return null;
        }
    }

    public void executeMessage(SendMessage message) {
        eventPublisher.publishEvent(new SendMessageEvent(message));
        log.info("Публикация события SendMessageEvent для executeMessage");
    }

    public Integer executeCaptchaMessage(SendMessage message) {
        SendCaptchaMessageEvent sendCaptchaMessageEvent = new SendCaptchaMessageEvent(message);
        eventPublisher.publishEvent(sendCaptchaMessageEvent);

        return sendCaptchaMessageEvent.getMessageId();
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

        eventPublisher.publishEvent(new SendAnimationEvent(sendAnimation));
        log.info("Публикация события SendAnimationEvent для чата {}", chatId);
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

    public Map<Long, List<Message>> getUserMessages() {
        return userMessages;
    }

    public void clearUserMessages() {
        userMessages.clear();
    }

    public void addUserMessage(Long userId, Message message) {
        userMessages.putIfAbsent(userId, new ArrayList<>());
        userMessages.get(userId).add(message);
    }

    public void deleteUserMessages(Long chatId, Long userId) {
        for (Map.Entry<Long, List<Message>> entry : userMessages.entrySet()) {
            if (entry.getKey().equals(userId)) {
                List<Message> messages = entry.getValue();
                for (Message message : messages) {
                    DeleteMessage deleteMessage = new DeleteMessage();
                    deleteMessage.setChatId(String.valueOf(chatId));
                    deleteMessage.setMessageId(message.getMessageId());
                    eventPublisher.publishEvent(new DeleteMessageEvent(deleteMessage));
                    log.info("Публикация события DeleteMessageEvent для удаления сообщения ID {}", message.getMessageId());
                }
                messages.clear();
            }
        }
    }
}