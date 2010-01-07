package net.yacy.ai.example;

import java.util.Random;
import java.util.concurrent.PriorityBlockingQueue;

public class testorder implements Comparable<testorder> {

    public int x;
    public testorder(int x) {
        this.x = x;
    }
    public String toString() {
        return Integer.toString(this.x);
    }

    public int compareTo(testorder o) {
        if (this.x > o.x) return 1;
        if (this.x < o.x) return -1;
        return 0;
    }
    
    public static void main(String[] args) {
        PriorityBlockingQueue<testorder> q = new PriorityBlockingQueue<testorder>();
        Random r = new Random();
        for (int i = 0; i < 10; i++) {
            q.add(new testorder(r.nextInt(20)));
        }
        while (!q.isEmpty())
            try {
                System.out.println(q.take().toString());
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
    }
}
