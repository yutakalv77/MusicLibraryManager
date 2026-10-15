package com.musiclibrary.repository;

import com.musiclibrary.model.Playlist;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing user playlists and their track associations.
 */
public interface PlaylistRepository {

    Optional<Playlist> findById(String id);

    List<Playlist> findByAccountId(String accountId);

    void save(Playlist playlist);

    void deleteById(String id);
}
