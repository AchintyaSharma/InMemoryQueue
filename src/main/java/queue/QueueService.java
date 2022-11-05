package queue;

import java.util.HashMap;
import java.util.Map;

public class QueueService {

    private static Map<String, Queue> queues = new HashMap<>();

    private static int defaultCapacity = 5;

    public static Queue getQueue(String queueName) {
        return queues.get(queueName);
    }

    public static void initializeQueue(String queueName, int capacity) {
        queues.put(queueName, new QueueImpl(capacity));
    }

}
