package com.kaminsky.model.repositories;

import com.kaminsky.model.User;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
}
