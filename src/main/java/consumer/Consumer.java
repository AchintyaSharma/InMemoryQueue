package consumer;

import exception.ConsumerAlreadySubscribedException;
import exception.InvalidDependentConsumers;
import queue.Queue;
import queue.QueueService;
import util.Constants;

public abstract class Consumer {
    private Queue cache;
    private String consumerName;

    Consumer(String consumerName) {
        this.cache = QueueService.getQueue(Constants.QueueType.CACHE);
        this.consumerName = consumerName;
    }

    public void subscribe(String messageName, String callbackMethod, Consumer... dependentConsumers) {
        try {
            cache.subscribe(this, messageName, callbackMethod, dependentConsumers);
            System.out.println(this.getConsumerName() + " subscribed to message - " + messageName);
            for (Consumer dependentConsumer : dependentConsumers) {
                System.out.println("\tDepends on - " + dependentConsumer.getConsumerName());
            }
        } catch (InvalidDependentConsumers | ConsumerAlreadySubscribedException e) {
            System.err.println(e.getMessage());
        }
    }

    public String getConsumerName() {
        return this.consumerName;
    }
}
