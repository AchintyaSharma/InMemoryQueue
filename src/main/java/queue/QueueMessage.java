package queue;

import lombok.*;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class QueueMessage {
    private String messageName;
    private int retryCount;
    private String payload;
    private Date creationTimestamp;
    private int ttl;
}
