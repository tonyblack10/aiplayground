package io.tonyblack10.aiplayground.chat.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record ChatMessage(String role, String content, String timestamp) {

  public static ChatMessage user(String content) {
    return new ChatMessage("user", content, now());
  }

  public static ChatMessage assistant(String content) {
    return new ChatMessage("assistant", content, now());
  }

  private static String now() {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
  }
}
