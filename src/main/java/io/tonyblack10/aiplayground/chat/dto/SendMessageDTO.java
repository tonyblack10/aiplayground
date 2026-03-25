package io.tonyblack10.aiplayground.chat.dto;

import java.util.List;

public record SendMessageDTO(
    String message,
    String provider,
    String model,
    double temperature,
    String ragStoreId,
    List<String> tools
) {}
