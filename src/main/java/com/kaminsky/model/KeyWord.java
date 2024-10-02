package com.kaminsky.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "keyWordsTable")
public class KeyWord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String keyWord;

}
