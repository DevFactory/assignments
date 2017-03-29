package ru.caching;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * by Anatolii Danilov on 29-3-2017.
 * Есть кэш в котором хранятся картинки (например, для социально сети). Нужно написать реализацию метода putToCache который сохраняет картинку в кэш и
 * возвращает ее id, чтобы потом можно было по идентификатору запросить сохраненную картинку.
 * Также необходимо реализовать парный ему метод getFromCache, который возвращает сохраненную картинку по ее идентификатору.
 * При отсутствии данных в кэше, возвращать null.
 * Кэш должен при достижении лимита по памяти начинать использовать диск для хранения данных.
 * <p>
 * Методы loadFromFile, saveToFile уже реализованы, но они не являются thread-safe, и, что хуже, saveToFile работает 1000ms.
 */
public class MyCache {

    private AtomicInteger sequenceId = new AtomicInteger();
    private AtomicLong cacheSize = new AtomicLong();

    private final Long limit = Long.parseLong(System.getProperty("ru.caching.limit", "102400"));

    private ConcurrentHashMap<Integer, byte[]> cache = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, String> fileCache = new ConcurrentHashMap<>();
    private ExecutorService loadFromFileSystem = Executors.newFixedThreadPool(1);
    private ExecutorService saveToFilesystemService = Executors.newFixedThreadPool(1);

    private Map<String, byte[]> fileSystem = new HashMap<>();


    public byte[] loadFromFile(String filename) {
        return fileSystem.get(filename);
    }

    public void saveToFile(String filename, byte[] data) {
        try {
            Thread.sleep(1000);
            fileSystem.put(filename, data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int putToCache(byte[] data) {
        int nextId = sequenceId.incrementAndGet();

        long beforeAdding = cacheSize.get();
        long newSize = beforeAdding + data.length;
        if (newSize > limit) {
            submitFileSave(data, nextId);
            return nextId;
        }

        while (!cacheSize.compareAndSet(beforeAdding, newSize)) {
            beforeAdding = cacheSize.get();
            newSize = beforeAdding + data.length;
            if (newSize > limit) {
                submitFileSave(data, nextId);
                return nextId;
            }
        }

        cache.put(nextId, data);
        return nextId;
    }

    private void submitFileSave(byte[] data, int nextId) {
        // save to file

        Future<?> submit = saveToFilesystemService.submit(() -> {
            String filename = "CH_" + nextId;
            saveToFile(filename, data);
            fileCache.put(nextId, filename);
        });

        try {
            submit.get();
        } catch (InterruptedException | ExecutionException e) {
            // handling
        }
    }

    public byte[] getFromCache(int id) {
        byte[] bytes = cache.get(id);
        if (bytes != null) {
            return bytes;
        }
        if (fileCache.containsKey(id)) {
            try {
                return loadFromFileSystem.submit(() -> loadFromFile(fileCache.get(id))).get();
            } catch (InterruptedException | ExecutionException e) {
                // handling
            }
        }
        return null;
    }

    public long getCacheSize(){
        return cacheSize.get();
    }


}
