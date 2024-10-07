package com.kaminsky.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Slf4j
@Component
public class CallbackQueryHandler {

    private final CaptchaService captchaService;
    private final AdminService adminService;

    @Autowired
    public CallbackQueryHandler(CaptchaService captchaService,
                                AdminService adminService,
                                MessageService messageService) {
        this.captchaService = captchaService;
        this.adminService = adminService;
    }

    public void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (data.startsWith("CONFIRM_BUTTON")) {
            captchaService.handleConfirmButton(callbackQuery);
        } else if (data.startsWith("WELCOME_TEXT_BUTTON")) {
            adminService.handleSetWelcomeText(callbackQuery);
        } else if (data.startsWith("RECURRENT_TEXT_BUTTON")) {
            adminService.handleSetRecurrentText(callbackQuery);
        } else {
            adminService.handleConfigCallbackQuery(callbackQuery);
        }
    }
}