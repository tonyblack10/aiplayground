package io.tonyblack10.aiplayground.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

class FilterExpressionValidatorTest {

  @Test
  void validExpression_isParsedAndReturned() {
    Filter.Expression result = FilterExpressionValidator.parseAndValidate("source == 'readme.md'");

    assertThat(result).isNotNull();
    assertThat(result.type()).isEqualTo(Filter.ExpressionType.EQ);
  }

  @Test
  void unknownField_throwsWithActionableMessage() {
    assertThatThrownBy(() -> FilterExpressionValidator.parseAndValidate("branch == 'main'"))
        .isInstanceOf(InvalidFilterExpressionException.class)
        .hasMessageContaining("branch")
        .hasMessageContaining("source");
  }

  @Test
  void malformedSyntax_throwsActionableParseError() {
    assertThatThrownBy(() -> FilterExpressionValidator.parseAndValidate("source =="))
        .isInstanceOf(InvalidFilterExpressionException.class)
        .hasMessageContaining("Could not parse");
  }

  @Test
  void nestedAndOrNot_collectsAllKeys_andRejectsUnknownNestedField() {
    assertThatThrownBy(() -> FilterExpressionValidator.parseAndValidate(
            "source == 'readme.md' AND spaceKey == 'ENG' OR NOT folders == 'x'"))
        .isInstanceOf(InvalidFilterExpressionException.class)
        .hasMessageContaining("folders");
  }

  @Test
  void allKnownFieldsAcrossAndOrIn_pass() {
    Filter.Expression result = FilterExpressionValidator.parseAndValidate(
        "source == 'readme.md' AND spaceKey == 'ENG' OR boardId IN ['1', '2']");

    assertThat(result).isNotNull();
  }
}
