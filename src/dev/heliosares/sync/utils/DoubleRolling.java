package dev.heliosares.sync.utils;

public class DoubleRolling {

    private final int size;
    private final double total = 0d;
    private int index = 0;

    private final int[] samples;
    private final int[] time;

    public DoubleRolling(int size) {
        this.size = size;
        samples = new int[size];
        time = new int[size];
    }

    public void increment() {
        samples[index]++;
    }

    public void advance(int time) {
        this.time[index] = time;
        if (++index == size)
            index = 0;
    }

    public double getAverage() {
        return total / size;
    }
}