package ru.mail.polis.vlasova;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.NoSuchElementException;

public interface DAO {
    @NotNull
    byte[] get(@NotNull String key) throws NoSuchElementException, IOException;

    void upsert(@NotNull String key, @NotNull byte[] value) throws IllegalArgumentException, IOException;

    void delete(@NotNull String key) throws IOException;
}
