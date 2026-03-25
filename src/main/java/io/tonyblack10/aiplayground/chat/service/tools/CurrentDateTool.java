package io.tonyblack10.aiplayground.chat.service.tools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CurrentDateTool {

  @Tool(description = "Returns the current date and time in yyyy-MM-dd HH:mm:ss format")
  public String getCurrentDateTime() {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
  }
}
