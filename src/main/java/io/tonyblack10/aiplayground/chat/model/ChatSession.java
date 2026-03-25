package io.tonyblack10.aiplayground.chat.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatSession {

  private final List<ChatMessage> messages = new ArrayList<>();
  private ChatSettings settings = ChatSettings.defaults();

  public void addUserMessage(String content) {
    messages.add(ChatMessage.user(content));
  }

  public void addAssistantMessage(String content) {
    messages.add(ChatMessage.assistant(content));
  }

  public List<ChatMessage> getMessages() {
    return Collections.unmodifiableList(messages);
  }

  public ChatSettings getSettings() {
    return settings;
  }

  public void updateSettings(ChatSettings settings) {
    this.settings = settings;
  }

  public void clear() {
    messages.clear();
  }

  public boolean isEmpty() {
    return messages.isEmpty();
  }

  public boolean hasMessages() {
    return !messages.isEmpty();
  }
}
