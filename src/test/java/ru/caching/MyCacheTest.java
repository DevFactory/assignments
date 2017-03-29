package ru.caching;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

/**
 * by Anatolii Danilov on 29-3-2017.
 */
public class MyCacheTest {

    private MyCache myCache;

    @Before
    public void setUp() throws Exception {
        myCache = new MyCache();
    }

    @Test
    public void smallImage() throws Exception {
        int id1 = myCache.putToCache(new byte[100]);
        int id2 = myCache.putToCache(new byte[101]);
        int id3 = myCache.putToCache(new byte[102]);
        assertEquals(100, myCache.getFromCache(id1).length);
        assertEquals(101, myCache.getFromCache(id2).length);
        assertEquals(102, myCache.getFromCache(id3).length);

        assertEquals(303, myCache.getCacheSize());
    }

    @Test
    public void bigImage() throws Exception {
        int id3 = myCache.putToCache(new byte[102]);
        assertEquals(102, myCache.getFromCache(id3).length);

        byte[] image = new byte[1_000_000];
        int i = myCache.putToCache(image);
        byte[] thatImage = myCache.getFromCache(i);
        assertEquals(image.length, thatImage.length);
        assertEquals(102, myCache.getCacheSize());
    }


    @Test
    public void sameTimeTwoBigImages() throws Exception {
        int id3 = myCache.putToCache(new byte[127]);
        assertEquals(127, myCache.getFromCache(id3).length);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(2);


        new Thread(() -> addBig(start, ready)).start();
        new Thread(() -> addBig(start, ready)).start();
        start.countDown();

        ready.await();
        assertEquals(80127, myCache.getCacheSize());

    }

    private void addBig(CountDownLatch start, CountDownLatch ready) {
        try {
            start.await();
        } catch (InterruptedException e) {
            // handle
        }
        myCache.putToCache(new byte[80000]);
        ready.countDown();
    }
}