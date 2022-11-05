package exception;

public class MessageExpiredException extends Throwable {
    public MessageExpiredException(String exceptionMessage) {
        super(exceptionMessage);
    }
}
