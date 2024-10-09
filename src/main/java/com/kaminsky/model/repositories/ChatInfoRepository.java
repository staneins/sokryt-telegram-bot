package com.kaminsky.model.repositories;

import com.kaminsky.model.ChatInfo;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatInfoRepository extends CrudRepository<ChatInfo, Long> {
    @Override
    @Cacheable(value = "chatInfo", key = "#id")
    Optional<ChatInfo> findById(Long id);

    @Override
    @Cacheable(value = "chatInfoExists", key = "#id")
    boolean existsById(Long id);

    @Override
    @CachePut(value = "chatInfo", key = "#entity.chatId")
    <S extends ChatInfo> S save(S entity);

    @Override
    @CacheEvict(value = "chatInfo", key = "#id")
    void deleteById(Long id);
}
