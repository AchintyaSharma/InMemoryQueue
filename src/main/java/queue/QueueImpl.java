package queue;

import consumer.Consumer;
import exception.*;
import org.codehaus.jackson.map.ObjectMapper;
import util.BQueue;
import util.Constants;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QueueImpl implements Queue {
    private Map<String, MessageSubscribersDetails> messagesSubscribers;
    private BQueue<String> inMemoryQueue;
    private ObjectMapper objectMapper;

    public QueueImpl(int queueCapacity) {
        this.inMemoryQueue = new BQueue<>(queueCapacity);
        messagesSubscribers = new ConcurrentHashMap<>();
        objectMapper = new ObjectMapper();
        System.out.println("Queue Created with total capacity - " + inMemoryQueue.remainingCapacity());
    }

    @Override
    public void setQueueCapacity(int newCapacity) {
        synchronized (this) {
            try {
                checkIfNewCapacityIsValid(newCapacity);
                BQueue<String> newQueue = new BQueue<>(newCapacity);
                while (!inMemoryQueue.isEmpty()) {
                    newQueue.enqueue(inMemoryQueue.remove());
                }
                inMemoryQueue = newQueue;
                System.out.println("Changed Queue capacity to "+newCapacity);
            } catch (InterruptedException e) {
                System.err.println("Exception occurred during resizing of Queue. Queue not resized.");
            } catch (NewQueueCapacityCannotBeLessThanCurrentQueueSize e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private void checkIfNewCapacityIsValid(int newCapacity) throws NewQueueCapacityCannotBeLessThanCurrentQueueSize {
        if (newCapacity < inMemoryQueue.size()) {
            throw new NewQueueCapacityCannotBeLessThanCurrentQueueSize("Cannot set queue capacity to - " + newCapacity + " as it is less than current queue size - " + inMemoryQueue.size());
        }
    }

    /*
        Add Message to Queue with its unique messageName for which the consumers have subscribed for.
        Convert the Message to Json and add to Queue
     */
    @Override
    public void addMessage(QueueMessage queueMessage) {
        try {
            String queueMessageJson = getJsonMessage(queueMessage);
            inMemoryQueue.enqueue(queueMessageJson);
            System.out.println("Produced Message - " + queueMessage);
            startConsumerThread();
        } catch (InterruptedException | IOException e) {
            System.err.println("Exception occurred during addition of message with messageName -" + queueMessage.getMessageName() + " to queue, discarding message");
        }
    }

    private String getJsonMessage(QueueMessage queueMessage) throws IOException {
        return objectMapper.writeValueAsString(queueMessage);
    }

    private void startConsumerThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                consume();
            }
        }).start();
    }

    /*
        Consume topmost message from Queue. As its Blocking queue, it will wait if queue is empty.
        1. Read Json from Queue
        2. Convert it to QueueMessage
        3. Check If retry counter is less than 0. If yes, discard message
        4. Check If subscriber list for this message is empty, if yes, then throw NoSubscriberFoundException
        5. For all subscribers, start a new thread and call their callBack Method
            5.1 For each subscriber, wait till its dependent subscriber is finished with consumption.
            5.2 When subscriber is done consuming, remove subscriber from subscriberSet.
                So that when dependent subscriber checks this set, it comes to know that this subscribed has already consumed
         6. If message is not subscribed by any consumer, add back to queue by decrementing retryCounter by 1
     */
    private void consume() {
        String jsonQueueMessage = null;
        QueueMessage queueMessage = null;
        try {
            //1. Get Message From Queue
            jsonQueueMessage = inMemoryQueue.dequeue();
            //2. Convert JsonMessage it to QueueMessage
            queueMessage = objectMapper.readValue(jsonQueueMessage, QueueMessage.class);
            //3. Check If Message is Valid for consumption.
            checkIfMessageIsValid(queueMessage);
            //4. Get subscribed consumers for this message
            final String messageName = queueMessage.getMessageName();
            final String payload = queueMessage.getPayload();
            final Date messageCreationTimeStamp = queueMessage.getCreationTimestamp();
            final int ttl = queueMessage.getTtl();

            MessageSubscribersDetails messageSubscribersDetails = messagesSubscribers.get(messageName);
            //5. Check if subscriber list for this message is empty, if yes, then throw NoSubscriberFoundException
            checkIfAnyConsumerHasSubscribedToMessage(messageName, messageSubscribersDetails);

            checkIfMessageIsNotExpired(messageName, messageCreationTimeStamp, ttl);
            //6. For all subscribers, call their callback method for consumption
            //Clone the MessageSubscribersDetails, so when consumers are removed from subscriberSet during consumption, original list remains intact for other messages
            final MessageSubscribersDetails messageSubscriber = (MessageSubscribersDetails) messageSubscribersDetails.clone();
            for (Map.Entry<Consumer, String> subscribedConsumer : messageSubscriber.getConsumerDetails().entrySet()) {
                //Get CallBackMethodDetails details
                final Consumer consumer = subscribedConsumer.getKey();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        consume(payload, messageName, consumer, messageSubscriber);
                    }
                }).start();
            }

        } catch (InterruptedException e) {
            System.err.println("InterruptedException occurred during consumption of message from queue, discarding message");
        } catch (NoSubscriberFoundException e) {
            System.err.println(e.getMessage());
            retryMessage(queueMessage);
        } catch (MessageExpiredException e) {
            System.err.println(e.getMessage());
        } catch (IOException e) {
            System.err.println("IOException occurred during conversion of json message " + jsonQueueMessage + " to Java object, discarding message");
        } catch (MessageNotValidException e) {
            System.err.println(e.getMessage());
        } catch (CloneNotSupportedException e) {
            System.err.println("Exception occurred during consumption of message from queue, discarding message");
        }
    }

    private void consume(String payload, String messageName, Consumer consumer, MessageSubscribersDetails messageSubscribersDetails) {
        Set<Consumer> subscribedConsumers = messageSubscribersDetails.getSubscribedConsumers();
        synchronized (subscribedConsumers) {
            try {
                /*
                    Wait till all dependent Subscribers are done with their consumption
                 */
                for (Consumer dependentConsumer : messageSubscribersDetails.getDependentConsumers().get(consumer)) {
                    while (subscribedConsumers.contains(dependentConsumer)) {
                        subscribedConsumers.wait();
                    }
                }

                /*
                  Call callback method of consumer for consumption and supress any exception if occurred
                 */
                String callBackMethod = messageSubscribersDetails.getConsumerDetails().get(consumer);
                try {
                    consumer.getClass().getMethod(callBackMethod, String.class).invoke(consumer, payload);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    System.err.println("Exception occurred during consumption of messageFromQueue - " + messageName +
                            " Cannot find callback method " + callBackMethod + " for consumer - " + consumer.getConsumerName());
                } catch (Exception e) {
                    System.err.println("Exception occurred during consumption of messageFromQueue for " + consumer.getConsumerName() + " for message " + messageName);
                }
            } catch (InterruptedException e) {
                System.err.println("InterruptedException occurred during consumption of messageFromQueue for " + consumer.getConsumerName() + " for message " + messageName);
            }

            /*
                Remove current consumer from subscribedConsumers as it will let others know that this consumer has finished consuming
             */
            subscribedConsumers.remove(consumer);
            /*
                notify all other threads
             */
            subscribedConsumers.notifyAll();
        }
    }


    private void checkIfMessageIsValid(QueueMessage queueMessage) throws MessageNotValidException, InterruptedException {
        if (queueMessage.getRetryCount() <= 0) {
            QueueService.getQueue(Constants.QueueType.DEAD_LETTER_QUEUE).getBQueue().enqueue(queueMessage.getMessageName());
            throw new MessageNotValidException("Message " + queueMessage + " has maxed out retryCount. Discarding it");
        }
    }

    /*
        Decrement retry count by 1 and add message back to queue by converting it into Json
     */
    private void retryMessage(QueueMessage queueMessage) {
        queueMessage.setRetryCount(queueMessage.getRetryCount() - 1);
        try {
            String jsonMessage = getJsonMessage(queueMessage);
            inMemoryQueue.enqueue(jsonMessage);
            System.out.println("Added Message - " + jsonMessage+" back to Queue");
            startConsumerThread();
        } catch (InterruptedException | IOException e) {
            System.err.println("Exception occurred during addition of message with messageName -" + queueMessage.getMessageName() + " to queue, discarding message");
        }
    }

    private void checkIfAnyConsumerHasSubscribedToMessage(String messageName, MessageSubscribersDetails messageSubscribers) throws NoSubscriberFoundException {
        if (messageSubscribers == null || messageSubscribers.getSubscribedConsumers() == null || messageSubscribers.getSubscribedConsumers().size() == 0) {
            throw new NoSubscriberFoundException("No Consumer has subscribed to message -" + messageName);
        }
    }

    private void checkIfMessageIsNotExpired(String messageName, Date messageCreationTimestamp, int ttl) throws MessageExpiredException {
        if ((new Date().getTime() - messageCreationTimestamp.getTime())/1000 > ttl) {
            throw new MessageExpiredException("Message - " + messageName + " is expired. Removed from CACHE");
        }
    }


    @Override
    public void subscribe(Consumer consumer, String messageName, String callbackMethod, Consumer... dependentConsumers) throws InvalidDependentConsumers, ConsumerAlreadySubscribedException {
        /*
            1. Check If messageName is already present in our subscriptionList
         */
        MessageSubscribersDetails messageSubscribers = this.messagesSubscribers.get(messageName);
        if (messageSubscribers == null ||  messageSubscribers.getSubscribedConsumers() == null || messageSubscribers.getSubscribedConsumers().isEmpty()) {
            /*
                2.1  If subscription List is empty, then check whether Input Consumer has specified any dependent Consumers for subscription.
                    If Dependent Consumer List is not empty, then throw DependentConsumersNotYetSubscribed exception
            */
            checkIfDependentConsumerListIsEmptyForNewSubscription(consumer, messageName, dependentConsumers);

            /*
                2.2  For new subscription of Message, Create a new Entry in our subscriber Map with consumer details and its callbackMethod
             */
            Map<Consumer, String> newMessageSubscriber = new ConcurrentHashMap<>();
            newMessageSubscriber.put(consumer, callbackMethod);
            Map<Consumer, List<Consumer>> dependentMessageConsumers = new ConcurrentHashMap<>();
            dependentMessageConsumers.put(consumer, new LinkedList<Consumer>());
            Set<Consumer> subscribedConsumers = new HashSet<>();
            subscribedConsumers.add(consumer);
            messageSubscribers = new MessageSubscribersDetails(newMessageSubscriber, dependentMessageConsumers, subscribedConsumers);
            this.messagesSubscribers.put(messageName, messageSubscribers);
        } else {

            /*
                3.1 If Message is already present  in our subscription list, check if dependent consumers have subscribed to it
                    If Not, throw DependentConsumersNotYetSubscribed exception
             */
            checkIfDependentConsumersAreValidAndSubscribedForExistingMessage(consumer, messageName, messageSubscribers, dependentConsumers);
            /*
                3.2 If Message already present and all dependentConsumers are subscribed to it, then add this consumer to subscription list
             */

            messageSubscribers.getSubscribedConsumers().add(consumer);
            messageSubscribers.getDependentConsumers().put(consumer, Arrays.asList(dependentConsumers));
            messageSubscribers.getConsumerDetails().put(consumer, callbackMethod);
        }
    }

    private void checkIfDependentConsumerListIsEmptyForNewSubscription(Consumer consumer, String messageName, Consumer[] dependentConsumers) throws InvalidDependentConsumers {
        if (dependentConsumers != null && dependentConsumers.length != 0) {
            throw new InvalidDependentConsumers("Dependent Consumers for Consumer " + consumer.getConsumerName() + " have not yet subscribed to message - " + messageName);
        }
    }

    private void checkIfDependentConsumersAreValidAndSubscribedForExistingMessage(Consumer givenConsumer, String messageName, MessageSubscribersDetails messageSubscribers, Consumer[] dependentConsumers) throws InvalidDependentConsumers {
        Set<Consumer> subscribedConsumers = messageSubscribers.getSubscribedConsumers();
        List<Consumer> dependentConsumerList = Arrays.asList(dependentConsumers);
        if (dependentConsumerList.contains(givenConsumer)) {
            String exceptionMessage = "Subscription of message failed for Consumer "+givenConsumer.getConsumerName()+" for Message "+messageName;
            exceptionMessage += " Because, Dependent consumer is same as given consumer";
            throw new InvalidDependentConsumers(exceptionMessage);
        }
        else if (!subscribedConsumers.containsAll(dependentConsumerList)) {
            String exceptionMessage = "Subscription of message failed for Consumer "+givenConsumer.getConsumerName()+" for Message "+messageName;
            exceptionMessage += " Because, Dependent Consumers for Input Consumer - " + givenConsumer.getConsumerName() + " have not yet subscribed to message - " + messageName;
            throw new InvalidDependentConsumers(exceptionMessage);
        }

    }

    public BQueue<String> getBQueue() {
        return this.inMemoryQueue;
    }
}