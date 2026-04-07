package io.tonyblack10.aiplayground.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

@Component
public class RedisChatMemory implements ChatMemoryRepository {

  private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);
  private static final String KEY_PREFIX = "chat:memory:";
  private static final long TTL_SECONDS = 3600;

  private final JedisPooled jedis;
  private final ObjectMapper objectMapper;

  public RedisChatMemory(JedisPooled jedisPooled, ObjectMapper objectMapper) {
    this.jedis = jedisPooled;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<String> findConversationIds() {
    return jedis.keys(KEY_PREFIX + "*").stream()
        .map(k -> k.substring(KEY_PREFIX.length()))
        .toList();
  }

  @Override
  public List<Message> findByConversationId(String conversationId) {
    String key = KEY_PREFIX + conversationId;
    long len = jedis.llen(key);
    if (len == 0) return Collections.emptyList();

    List<String> jsons = jedis.lrange(key, 0, -1);
    List<Message> messages = new ArrayList<>();
    for (String json : jsons) {
      try {
        MessageDto dto = objectMapper.readValue(json, MessageDto.class);
        messages.add(toMessage(dto));
      } catch (JsonProcessingException e) {
        log.error("Failed to deserialize message from conversation {}", conversationId, e);
      }
    }
    return messages;
  }

  @Override
  public void saveAll(String conversationId, List<Message> messages) {
    String key = KEY_PREFIX + conversationId;
    jedis.del(key);
    if (messages.isEmpty()) return;

    try {
      String[] jsons = new String[messages.size()];
      for (int i = 0; i < messages.size(); i++) {
        Message m = messages.get(i);
        jsons[i] = objectMapper.writeValueAsString(
            new MessageDto(m.getMessageType().getValue(), m.getText()));
      }
      jedis.rpush(key, jsons);
      jedis.expire(key, TTL_SECONDS);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize messages for conversation {}", conversationId, e);
    }
  }

  @Override
  public void deleteByConversationId(String conversationId) {
    jedis.del(KEY_PREFIX + conversationId);
  }

  private Message toMessage(MessageDto dto) {
    return switch (dto.role()) {
      case "user" -> new UserMessage(dto.content());
      case "assistant" -> new AssistantMessage(dto.content());
      case "system" -> new SystemMessage(dto.content());
      default -> new UserMessage(dto.content());
    };
  }

  record MessageDto(String role, String content) {}
}
