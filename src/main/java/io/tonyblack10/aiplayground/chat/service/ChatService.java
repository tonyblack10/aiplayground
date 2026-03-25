package io.tonyblack10.aiplayground.chat.service;

import io.tonyblack10.aiplayground.chat.model.ChatMessage;
import io.tonyblack10.aiplayground.chat.model.ChatSettings;
import io.tonyblack10.aiplayground.chat.service.tools.CalculatorTool;
import io.tonyblack10.aiplayground.chat.service.tools.CurrentDateTool;
import io.tonyblack10.aiplayground.rag.service.VectorStoreRegistry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

  private final ChatClient.Builder chatClientBuilder;
  private final VectorStoreRegistry vectorStoreRegistry;
  private final CalculatorTool calculatorTool;
  private final CurrentDateTool currentDateTool;

  public ChatService(ChatClient.Builder chatClientBuilder,
      VectorStoreRegistry vectorStoreRegistry,
      CalculatorTool calculatorTool,
      CurrentDateTool currentDateTool) {
    this.chatClientBuilder = chatClientBuilder;
    this.vectorStoreRegistry = vectorStoreRegistry;
    this.calculatorTool = calculatorTool;
    this.currentDateTool = currentDateTool;
  }

  public Flux<String> stream(List<ChatMessage> history, String userMessage, ChatSettings settings) {
    List<Message> historyMessages = history.stream()
        .map(msg -> switch (msg.role()) {
          case "user" -> (Message) new UserMessage(msg.content());
          case "assistant" -> (Message) new AssistantMessage(msg.content());
          default -> (Message) new UserMessage(msg.content());
        })
        .toList();

    var options = OpenAiChatOptions.builder()
        .model(settings.model())
        .temperature(settings.temperature())
        .build();

    var spec = chatClientBuilder.build()
        .prompt()
        .messages(historyMessages)
        .user(userMessage)
        .options(options);

    if (settings.hasRag()) {
      var vectorStore = vectorStoreRegistry.getStore(settings.ragStoreId());
      spec = spec.advisors(QuestionAnswerAdvisor.builder(vectorStore).build());
    }

    List<Object> tools = buildTools(settings);
    if (!tools.isEmpty()) {
      spec = spec.tools(tools.toArray());
    }

    return spec.stream().content();
  }

  private List<Object> buildTools(ChatSettings settings) {
    List<Object> tools = new ArrayList<>();
    if (settings.hasTool("calculator")) tools.add(calculatorTool);
    if (settings.hasTool("currentDate")) tools.add(currentDateTool);
    return tools;
  }
}
