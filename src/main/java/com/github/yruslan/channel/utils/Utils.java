package com.github.yruslan.channel.utils;

public class Utils {
    public static Thread runInThread(Interruptable f) {
        Thread thread = new Thread(() -> {
            try {
                f.run();
            } catch (InterruptedException ignored) {
            }
        });
        thread.start();
        return thread;
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}
