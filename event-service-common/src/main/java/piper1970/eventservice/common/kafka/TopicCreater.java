package piper1970.eventservice.common.kafka;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Helper class for building new topics with pre-existing partition-count, replication-factor, and retention time.
 */
@Builder
@RequiredArgsConstructor
public class TopicCreater {

  private final Integer partitions;
  private final Integer replicationFactor;
  private final String retentionMs;

  public NewTopic create(String topicName) {
    return TopicBuilder.name(topicName)
        .partitions(partitions)
        .replicas(replicationFactor)
        .config(TopicConfig.RETENTION_MS_CONFIG, retentionMs)
        .build();
  }
}
