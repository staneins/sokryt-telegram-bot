package com.kaminsky.model.repositories;

import com.kaminsky.model.ChatInfo;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatInfoRepository extends CrudRepository<ChatInfo, Long> {
}
