package ru.mail.polis.vlasova;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class FileDao implements DAO {
    @NotNull
    private final String dir;
    @NotNull
    private Map<String, byte[]> cache;
    private static final int CACHE_SIZE = 1000;

    public FileDao(@NotNull File data) {
        this.dir = data.getPath();
        this.cache = new HashMap<>(CACHE_SIZE);
    }

    @NotNull
    @Override
    public byte[] get(@NotNull String key) throws NoSuchElementException, IOException {
        Path path = Paths.get(dir, key);
        if (!Files.exists(path)) {
            throw new NoSuchElementException();
        }
        byte[] value = Files.readAllBytes(path);
        if (cache.size() == CACHE_SIZE) {
            cache.remove(cache.keySet().iterator().next());
        }
        cache.put(key, value);
        return value;
    }

    @Override
    public void upsert(@NotNull String key, @NotNull byte[] value) throws IOException {
        Files.write(Paths.get(dir, key), value);
    }

    @Override
    public void delete(@NotNull String key) throws IOException {
        Files.delete(Paths.get(dir, key));
    }
}
