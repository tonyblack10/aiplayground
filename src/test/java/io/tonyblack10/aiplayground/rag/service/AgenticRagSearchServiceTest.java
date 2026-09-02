package io.tonyblack10.aiplayground.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.tonyblack10.aiplayground.rag.config.AgenticRagProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

class AgenticRagSearchServiceTest {

  private final VectorStoreRegistry vectorStoreRegistry = mock(VectorStoreRegistry.class);
  private final VectorStore vectorStore = mock(VectorStore.class);

  private static AgenticRagProperties properties() {
    AgenticRagProperties props = new AgenticRagProperties();
    props.setEnabled(true);
    props.setNumberOfQueries(3);
    props.setComplexityWordThreshold(6);
    return props;
  }

  @Test
  void simpleQuery_skipsExpansion_andSearchesOnce() {
    when(vectorStoreRegistry.getStore("simpleVectorStore")).thenReturn(vectorStore);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(Document.builder().id("id1").text("content").metadata(Map.of()).build()));

    ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
    AgenticRagSearchService service = new AgenticRagSearchService(vectorStoreRegistry, chatClientBuilder, properties());

    List<Document> results = service.agenticSearch("simpleVectorStore", "redis config", 5, 0.0, null).block();

    assertThat(results).hasSize(1);
    verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    verifyNoInteractions(chatClientBuilder);
  }

  @Test
  void complexQuery_expandsFansOutAndDedupesAcrossSubQueries() {
    when(vectorStoreRegistry.getStore("simpleVectorStore")).thenReturn(vectorStore);

    Document docA = Document.builder().id("a").text("content a").metadata(Map.of()).score(0.9).build();
    Document docB = Document.builder().id("b").text("content b").metadata(Map.of()).score(0.8).build();
    Document docC = Document.builder().id("c").text("content c").metadata(Map.of()).score(0.95).build();
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(docA, docB, docC));

    ChatModel fakeChatModel = new FixedResponseChatModel(
        "how to set up redis for vector search\n"
            + "configuring a redis vector index for embeddings\n"
            + "tuning redis vector store similarity settings");
    ChatClient.Builder chatClientBuilder = ChatClient.builder(fakeChatModel);
    AgenticRagSearchService service = new AgenticRagSearchService(vectorStoreRegistry, chatClientBuilder, properties());

    List<Document> results = service.agenticSearch(
        "simpleVectorStore",
        "explain how to configure redis as a vector store and tune the similarity threshold",
        5, 0.0, null).block();

    assertThat(results).extracting(Document::getId).containsExactly("c", "a", "b");
    verify(vectorStore, atLeast(2)).similaritySearch(any(SearchRequest.class));
  }

  private record FixedResponseChatModel(String response) implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
      return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      return Flux.just(call(prompt));
    }
  }
}
