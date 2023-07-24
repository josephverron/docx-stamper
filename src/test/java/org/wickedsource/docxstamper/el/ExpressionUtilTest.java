package org.wickedsource.docxstamper.el;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.wickedsource.docxstamper.el.ExpressionUtil.stripExpression;

class ExpressionUtilTest {
	@Test
    void findsPlaceholders() {
		String text = "lorem ipsum ${placeholder1} lorem ipsum ${placeholder2}";

		List<String> placeholders = ExpressionUtil.findVariableExpressions(text);

		assertEquals(2, placeholders.size());
		assertEquals("${placeholder1}", placeholders.get(0));
		assertEquals("${placeholder2}", placeholders.get(1));
	}

	@Test
    void findsProcessorExpressions() {
		String text = "lorem ipsum #{expression1} lorem ipsum #{expression2}";

		List<String> placeholders = ExpressionUtil.findProcessorExpressions(text);

		assertEquals(2, placeholders.size());
		assertEquals("#{expression1}", placeholders.get(0));
		assertEquals("#{expression2}", placeholders.get(1));
	}

	@Test
    void findsPlaceholdersWithError() {
		String text = "lorem ipsum ${placeholder1} ${ lorem ipsum } ${placeholder2";

		List<String> placeholders = ExpressionUtil.findVariableExpressions(text);

		assertEquals(2, placeholders.size());
		assertEquals("${placeholder1}", placeholders.get(0));
		assertEquals("${ lorem ipsum }", placeholders.get(1));
	}

	@Test
    void returnsEmptyListOnEmptyText() {
		String text = "";

		List<String> placeholders = ExpressionUtil.findVariableExpressions(text);

		assertTrue(placeholders.isEmpty());
	}

	@Test
    void stripsExpressions() {
		String expressionValue = "myExpression";
		String expression = "${%s}".formatted(expressionValue);
		String expected = expressionValue;

		String actual = stripExpression(expression);

		assertEquals(expected, actual);
	}

	@Test
    void stripsNullExpressionThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> stripExpression(null));
	}

}