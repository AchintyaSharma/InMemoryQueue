import consumer.MessageConsumer;
import producer.MessageProducer;
import producer.Producer;
import queue.Queue;
import queue.QueueService;
import util.BQueue;
import util.Constants;

import java.util.Map;

public class TestApplication {
    public static void main(String[] args) throws InterruptedException {
        //1. Create Queue with capacity 2
        QueueService.initializeQueue(Constants.QueueType.CACHE, 2);
        QueueService.initializeQueue(Constants.QueueType.DEAD_LETTER_QUEUE, 20);

        //2. Create Producer
        Producer messageProducer = new MessageProducer();
        //3. Create 4 Consumers and name them
        MessageConsumer consumer1 = new MessageConsumer("consumer_1");
        MessageConsumer consumer2 = new MessageConsumer("consumer_2");
        MessageConsumer consumer3 = new MessageConsumer("consumer_3");
        MessageConsumer consumer4 = new MessageConsumer("consumer_4");

        /*4. Subscribe consumers to messages
        Consumer 4 depends on Consumer1,2,3; Consumer2 depends on Consumer 3; Consumer1 depends on Consumer3
        So order of Consumption should be Consumer3, Consumer1 || Consumer2, Consumer 4(end)*/
        consumer3.subscribe("m1","consume3");
        consumer2.subscribe("m1","consume2",consumer3);
        consumer1.subscribe("m1","consume1",consumer3);
        consumer4.subscribe("m1","consume4", consumer1,consumer2,consumer3);

        /*4.2
            Consumer 1 depends on Consumer 2 for Message "m2"
            So order should be Consumer2, Consumer1
         */
        consumer2.subscribe("m2", "consume2");
        consumer1.subscribe("m2", "consume1", consumer2);

        //4.3 Consumer 2 depends on itself for Message "m3" -- Expect Exception on console
        consumer2.subscribe("m3", "consume2",consumer2);

        //4.4 Consumer 1 provides wrong callBackMethod for Message "m4" -- Expect Exception on console
        consumer1.subscribe("m4", "consume2");

        //4.5 Consumer 2 subscribes for message "m4" without any dependencies -- Successfully consumed
        consumer2.subscribe("m4", "consume2");

        //4.6 Consumer 3 subscribed for message "m4" without its dependent consumer1 subscribing for it - Expect exception and subscribe is failed
        consumer3.subscribe("m4", "consume3", consumer1);

        //4.7 No one subscribed for message "m5"
        System.out.println();

        //5. Start producer Thread which produces messages - m1, m2, m3,m4 ,m5
        Thread producerThread = new Thread(messageProducer);
        producerThread.start();
        //6. Setting Queue to new Capacity
        Thread.sleep(80);
        QueueService.getQueue(Constants.QueueType.CACHE).setQueueCapacity(4);
        System.out.println("Exiting Main Application");

        BQueue<String> deadLetterQueue = QueueService.getQueue(Constants.QueueType.DEAD_LETTER_QUEUE).getBQueue();

        System.out.println("Dead Letter Queue Contents :: " + deadLetterQueue.size());
        while (!deadLetterQueue.isEmpty()) {
            System.out.println(deadLetterQueue.dequeue());
        }

    }
}

