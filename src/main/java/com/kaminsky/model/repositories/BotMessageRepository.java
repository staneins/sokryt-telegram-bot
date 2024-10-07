package com.kaminsky.model.repositories;

import com.kaminsky.model.BotMessage;
import org.springframework.data.repository.CrudRepository;

public interface BotMessageRepository extends CrudRepository<BotMessage, Long> {
}
