package producer;

import queue.*;
import util.Constants;

import java.util.Date;

public class MessageProducer implements Producer {
    private final Queue queue;

    public MessageProducer() {
        this.queue = QueueService.getQueue(Constants.QueueType.CACHE);
    }

    public void produce() {
        for (int index = 1; index <= 5; index++) {
            String messageName = "m"+index;
            QueueMessage queueMessage = QueueMessage.builder()
                    .messageName(messageName)
                    .payload("m"+index)
                    .retryCount(Constants.RETRY_COUNT)
                    .creationTimestamp(new Date())
                    .ttl(Constants.TTL).build();
            queue.addMessage(queueMessage);
            try {
                Thread.sleep(index*10);
            } catch (InterruptedException e) {
                System.err.println("InterruptedException occurred during making producer thread sleep, suppressing it");
            }
        }
        //Adding Message m1 again to see if Order of consumption is Consumer3,Consumer2/consumer1,consumer4
        //and subscription list shows correct value in multithreaded environment
        QueueMessage queueMessage = QueueMessage.builder()
                .messageName("m1")
                .payload("m1")
                .retryCount(Constants.RETRY_COUNT)
                .creationTimestamp(new Date())
                .ttl(Constants.TTL).build();
        queue.addMessage(queueMessage);
    }

    @Override
    public void run() {
        produce();
    }
}
