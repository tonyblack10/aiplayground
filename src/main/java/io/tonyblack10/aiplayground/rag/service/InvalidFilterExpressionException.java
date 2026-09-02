package io.tonyblack10.aiplayground.rag.service;

/** Thrown when a caller-supplied {@code filterExpression} is malformed or references an unknown field. */
public class InvalidFilterExpressionException extends RuntimeException {

  public InvalidFilterExpressionException(String message) {
    super(message);
  }
}
