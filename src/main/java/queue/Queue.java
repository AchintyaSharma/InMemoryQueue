package queue;

import consumer.Consumer;
import exception.ConsumerAlreadySubscribedException;
import exception.InvalidDependentConsumers;
import util.BQueue;

public interface Queue {
    void setQueueCapacity(int size);

    void addMessage(QueueMessage queueMessage);

    void subscribe(Consumer consumer, String messageName, String callbackMethod, Consumer... dependentConsumers) throws InvalidDependentConsumers, ConsumerAlreadySubscribedException;

    BQueue<String> getBQueue();
}
