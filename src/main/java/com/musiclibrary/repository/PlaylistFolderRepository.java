package com.musiclibrary.repository;

import com.musiclibrary.model.PlaylistFolder;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing PlaylistFolder entities.
 */
public interface PlaylistFolderRepository {

    Optional<PlaylistFolder> findById(String id);

    List<PlaylistFolder> findByAccountId(String accountId);

    List<PlaylistFolder> findByParentFolderId(String accountId, String parentFolderId);

    void save(PlaylistFolder folder);

    void saveAll(List<PlaylistFolder> folders);

    void deleteById(String id);
}
