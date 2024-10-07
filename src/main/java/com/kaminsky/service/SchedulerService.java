package com.kaminsky.service;

import com.kaminsky.model.BotMessage;
import com.kaminsky.model.repositories.BotMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SchedulerService {

    private final BotMessageRepository botMessageRepository;
    private final MessageService messageService;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
    private final UserService userService;

    @Autowired
    public SchedulerService(BotMessageRepository botMessageRepository,
                            MessageService messageService, UserService userService) {
        this.botMessageRepository = botMessageRepository;
        this.messageService = messageService;
        this.userService = userService;
    }

    @Scheduled(cron = "${cron.scheduler}")
    public void sendRecurrentMessage() {
        Iterable<BotMessage> botMessages = botMessageRepository.findAll();
        for (BotMessage botMessage : botMessages) {
            Long chatId = botMessage.getChatId();
            String recurrentMessage = botMessage.getRecurrentMessage();
            if (recurrentMessage != null && !recurrentMessage.trim().isEmpty()) {
                String fixedMessage = messageService.fixMarkdownText(recurrentMessage);
                messageService.sendMarkdownMessage(chatId, fixedMessage);
                log.info("Повторяющееся сообщение отправлено {}: {}", chatId, recurrentMessage);
            }
        }
    }

    /**
     * Планирует выполнение задачи с заданной задержкой.
     *
     * @param task  Задача для выполнения
     * @param delay Задержка перед выполнением
     * @param unit  Единица измерения задержки
     */
    public void scheduleTask(Runnable task, long delay, TimeUnit unit) {
        executorService.schedule(task, delay, unit);
        log.info("Scheduled task to run in {} {}", delay, unit);
    }

    public void shutdownScheduler() {
        if (!executorService.isShutdown() && executorService != null) {
            executorService.shutdown();
            log.info("Планировщик остановился");
        }
    }

    public void startBannedUsersCleanupTask(long interval, TimeUnit timeUnit) {
        executorService.scheduleAtFixedRate(() -> {
            userService.clearBannedUsers();
        }, interval, interval, timeUnit);
    }
}