package com.musiclibrary.repository;

import com.musiclibrary.model.Genre;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing musical genres.
 */
public interface GenreRepository {

    /**
     * Returns all registered genres sorted by name ascending.
     *
     * @return list of genres
     */
    List<Genre> findAll();

    /**
     * Returns all registered genre names sorted in ascending order.
     *
     * @return list of genre name strings
     */
    List<String> findAllNames();

    /**
     * Finds a genre by its name (case-insensitive).
     *
     * @param name the genre name
     * @return Optional containing the Genre if found, or empty Optional
     */
    Optional<Genre> findByName(String name);

    /**
     * Saves a genre (creates or updates).
     *
     * @param genre the genre to save
     * @return the saved Genre entity
     */
    Genre save(Genre genre);

    /**
     * Finds an existing genre by name (case-insensitive) or creates and saves a new one.
     *
     * @param name the genre name
     * @return the existing or newly created Genre
     */
    Genre findOrCreate(String name);

    /**
     * Checks if a genre with the given name exists (case-insensitive).
     *
     * @param name the genre name
     * @return true if exists, false otherwise
     */
    boolean existsByName(String name);
}
