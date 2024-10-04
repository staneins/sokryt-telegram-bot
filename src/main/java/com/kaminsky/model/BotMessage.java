package com.kaminsky.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "messagesTable")
public class BotMessage {
    @Id
    private Long chatId;

    @Column(length = 1000)
    private String welcomeMessage;

    @Column(length = 1000)
    private String recurrentMessage;
}
