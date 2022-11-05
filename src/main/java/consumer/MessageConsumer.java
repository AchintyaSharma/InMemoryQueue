package consumer;

public class MessageConsumer extends Consumer {

    public MessageConsumer(String consumerName) {
        super(consumerName);
    }

    public void consume1(String payload) {
        System.out.println("Callback method for consumer 1 :: " + this.getConsumerName() + " consumed Message -" + payload);
    }

    public void consume2(String payload) {
        System.out.println("Callback method for consumer 2 :: " + this.getConsumerName() + " consumed Message -" + payload);
    }

    public void consume3(String payload) {
        System.out.println("Callback method for consumer 3 :: " + this.getConsumerName() + " consumed Message -" + payload);
    }

    public void consume4(String payload) {
        System.out.println("Callback method for consumer 4 :: " + this.getConsumerName() + " consumed Message -" + payload);
    }
}
