package com.musiclibrary.repository;

import com.musiclibrary.model.Account;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing Account entities.
 */
public interface AccountRepository {

    Optional<Account> findById(String id);

    Optional<Account> findByName(String name);

    List<Account> findAll();

    void save(Account account);

    void deleteById(String id);

    Account getOrCreateDefaultAccount();
}
