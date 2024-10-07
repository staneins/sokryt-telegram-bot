package com.kaminsky.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "chatInfoTable")
public class ChatInfo {
    @Id
    private Long chatId;

    private String chatTitle;
}
