package com.kaminsky.service;

import com.kaminsky.finals.BotFinalVariables;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Slf4j
@Service
public class CallbackQueryHandler {

    private final CaptchaService captchaService;
    private final AdminService adminService;

    @Autowired
    public CallbackQueryHandler(CaptchaService captchaService,
                                AdminService adminService) {
        this.captchaService = captchaService;
        this.adminService = adminService;
    }

    public void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (data.startsWith(BotFinalVariables.CONFIRM_BUTTON)) {
            captchaService.handleConfirmButton(callbackQuery);
        } else if (data.startsWith(BotFinalVariables.WELCOME_TEXT_BUTTON)) {
            adminService.handleSetWelcomeText(callbackQuery);
        } else if (data.startsWith(BotFinalVariables.RECURRENT_TEXT_BUTTON)) {
            adminService.handleSetRecurrentText(callbackQuery);
        } else if (data.startsWith(BotFinalVariables.UNMUTE_BUTTON)) {
            adminService.handleUnmuteCommandCallbackQuery(callbackQuery);
        } else if (data.startsWith(BotFinalVariables.KEYS_BUTTON)) {
            adminService.handleKeyWordsCallbackQuery(callbackQuery);
        } else if (data.startsWith(BotFinalVariables.WIPE_KEYS_BUTTON)) {
            adminService.wipeAllKeys(callbackQuery);
        } else {
            adminService.handleConfigCallbackQuery(callbackQuery);
        }
    }
}