package com.kaminsky.service;

import com.kaminsky.config.BotConfig;
import com.kaminsky.model.BotMessage;
import com.kaminsky.model.User;
import com.kaminsky.model.repositories.BotMessageRepository;
import com.kaminsky.model.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final MessageService messageService;
    private final BotMessageRepository botMessageRepository;
    private final BotConfig botConfig;

    private boolean isCommandHandled = false;
    private boolean isAwaitingUnbanPetition = false;
    private boolean isAwaitingWelcomeText = false;
    private boolean isAwaitingRecurrentText = false;
    private boolean isAwaitingKeyWords = false;

    private final Set<Long> bannedUsers = Collections.synchronizedSet(new HashSet<>());
    private final Map<Long, Boolean> userCaptchaStatus = new ConcurrentHashMap<>();

    private Long currentChatIdForWelcomeText = null;
    private Long currentChatIdForRecurrentText = null;


    @Autowired
    public UserService(UserRepository userRepository,
                       MessageService messageService,
                       BotMessageRepository botMessageRepository, BotConfig botConfig) {
        this.userRepository = userRepository;
        this.messageService = messageService;
        this.botMessageRepository = botMessageRepository;
        this.botConfig = botConfig;
    }

    public void registerUser(Message message) {
        if (message != null && message.getChat() != null) {
            Long chatId = message.getFrom().getId();
            if (userRepository.findById(chatId).isEmpty()) {
                User user = new User();
                user.setChatId(chatId);
                user.setFirstName(message.getChat().getFirstName());
                user.setLastName(message.getChat().getLastName());
                user.setUserName(message.getChat().getUserName());
                user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
                userRepository.save(user);
                log.info("Пользователь зарегистрирован: " + user);
            }
        } else {
            log.warn("Попытка зарегистрировать пользователя с пустым сообщением или чатом");
        }
    }

    public User getOrRegisterWarnedUser(Message message, Long warnedUserId) {
        if (message.getReplyToMessage() != null) {
            registerUser(message.getReplyToMessage());
            return userRepository.findById(warnedUserId).orElse(null);
        }
        return null;
    }

    public void sendToAllUsers(String textToSend) {
        Iterable<User> users = userRepository.findAll();
        for (User user : users) {
            messageService.sendMessage(user.getChatId(), textToSend);
        }
        log.info("Отправлено сообщение всем пользователям: " + textToSend);
    }

    public void saveWelcomeText(Long chatId, String welcomeText) {
            BotMessage botMessage = botMessageRepository.findById(chatId).orElse(new BotMessage());
            botMessage.setChatId(chatId);
            botMessage.setWelcomeMessage(welcomeText);
            botMessageRepository.save(botMessage);
    }

    public void saveRecurrentText(Long chatId, String recurrentText) {
            BotMessage botMessage = botMessageRepository.findById(chatId).orElse(new BotMessage());
            botMessage.setChatId(chatId);
            botMessage.setRecurrentMessage(recurrentText);
            botMessageRepository.save(botMessage);
    }

    public void forwardUnbanPetition(Long chatId, Message message) {
        ForwardMessage forwardMessage = new ForwardMessage();
        forwardMessage.setChatId(botConfig.getOwnerId());
        forwardMessage.setFromChatId(chatId.toString());
        forwardMessage.setMessageId(message.getMessageId());
        messageService.executeForwardMessage(forwardMessage);
    }

    public void collectUserMessage(Long chatId, Message message) {
        messageService.addUserMessage(chatId, message);
    }

    public void collectAllMessages(Long chatId, Message message) {
        messageService.addMessage(chatId, message);
    }

    public void setUserCaptchaStatus(Long chatId, Boolean isPassedCaptcha) {
        userCaptchaStatus.put(chatId, isPassedCaptcha);
    }

    public Boolean getUserCaptchaStatus(Long chatId) {
        return userCaptchaStatus.get(chatId);
    }

    public Long getCurrentChatIdForRecurrentText() {
        return currentChatIdForRecurrentText;
    }

    public void setCurrentChatIdForRecurrentText(Long currentChatIdForRecurrentText) {
        this.currentChatIdForRecurrentText = currentChatIdForRecurrentText;
    }

    public Long getCurrentChatIdForWelcomeText() {
        return currentChatIdForWelcomeText;
    }

    public void setCurrentChatIdForWelcomeText(Long currentChatIdForWelcomeText) {
        this.currentChatIdForWelcomeText = currentChatIdForWelcomeText;
    }

    public void addBannedUser(Long userId) {
        bannedUsers.add(userId);
    }

    public Set<Long> getBannedUsers() {
        return bannedUsers;
    }

    public void clearBannedUsers() {
        bannedUsers.clear();
        log.info("Список забаненных пользователей очищен");
    }

    public boolean isAwaitingRecurrentText() {
        return isAwaitingRecurrentText;
    }

    public void setAwaitingRecurrentText(boolean awaitingRecurrentText) {
        isAwaitingRecurrentText = awaitingRecurrentText;
    }

    public boolean isAwaitingKeyWords() {
        return isAwaitingKeyWords;
    }

    public void setAwaitingKeyWords(boolean awaitingKeyWords) {
        isAwaitingKeyWords = awaitingKeyWords;
    }

    public boolean isAwaitingWelcomeText() {
        return isAwaitingWelcomeText;
    }

    public void setAwaitingWelcomeText(boolean awaitingWelcomeText) {
        isAwaitingWelcomeText = awaitingWelcomeText;
    }

    public boolean isAwaitingUnbanPetition() {
        return isAwaitingUnbanPetition;
    }

    public void setAwaitingUnbanPetition(boolean awaitingUnbanPeition) {
        isAwaitingUnbanPetition = awaitingUnbanPeition;
    }

    public boolean isCommandHandled() {
        return isCommandHandled;
    }

    public void setCommandHandled(boolean commandHandled) {
        isCommandHandled = commandHandled;
    }

}
