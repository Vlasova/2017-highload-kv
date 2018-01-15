package ru.mail.polis.vlasova;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.NoSuchElementException;

public class FileDao implements DAO {
    @NotNull
    private final File dir;

    public FileDao(@NotNull File data) {
        this.dir = data;
    }

    @NotNull
    private File getFile(@NotNull String key) {
        return new File(dir, key);
    }

    @NotNull
    @Override
    public byte[] get(@NotNull String key) throws NoSuchElementException, IllegalArgumentException, IOException {
        File file = getFile(key);
        byte[] value = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(value, 0, fis.available());
        } catch (FileNotFoundException e) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public void upsert(@NotNull String key, @NotNull byte[] value) throws IllegalArgumentException, IOException {
        try (FileOutputStream fos = new FileOutputStream(getFile(key), false)) {
            fos.write(value);
        }
    }

    @Override
    public void delete(@NotNull String key) throws IllegalArgumentException, IOException {
        getFile(key).delete();
    }
}
