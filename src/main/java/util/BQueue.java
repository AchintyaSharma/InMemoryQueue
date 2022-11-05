package util;

import java.util.LinkedList;

public class BQueue<T> {

    private final LinkedList<T> queue = new LinkedList<>();
    private final int limit;

    public BQueue(int limit) {
        if (limit < 1) throw new IllegalStateException("limit should be greater than 0");
        this.limit = limit;
    }

    public void enqueue(T item) throws InterruptedException {
        synchronized(queue) {
            while (queue.size() == limit) {
                queue.wait();
            }
            if (queue.isEmpty()) {
                queue.notifyAll();
            }
            queue.addLast(item);
        }
    }

    public T dequeue() throws InterruptedException {
        synchronized(queue) {
            while (queue.isEmpty()) {
                queue.wait();
            }
            if (queue.size() == limit) {
                queue.notifyAll();
            }
            return queue.removeFirst();
        }
    }

    public int remainingCapacity() {
        return limit - queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized T remove() {
        return queue.remove();
    }

    public int size() {
        return queue.size();
    }
}
