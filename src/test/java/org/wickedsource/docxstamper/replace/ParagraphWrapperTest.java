package org.wickedsource.docxstamper.replace;

import org.junit.jupiter.api.Test;
import org.wickedsource.docxstamper.util.ParagraphWrapper;
import org.wickedsource.docxstamper.util.RunUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.wickedsource.docxstamper.util.ParagraphUtil.create;

public class ParagraphWrapperTest {

	@Test
	public void getTextReturnsAggregatedText() {
		ParagraphWrapper aggregator = loremIpsum();
		assertEquals("lorem ipsum", aggregator.getText());
	}

	private ParagraphWrapper loremIpsum() {
		return new ParagraphWrapper(create("lorem", " ", "ipsum"));
	}

	@Test
	public void getRunsReturnsAddedRuns() {
		ParagraphWrapper aggregator = loremIpsum();
		assertEquals(3, aggregator.getRuns().size());
		assertEquals("lorem", RunUtil.getText(aggregator.getRuns().get(0)));
		assertEquals(" ", RunUtil.getText(aggregator.getRuns().get(1)));
		assertEquals("ipsum", RunUtil.getText(aggregator.getRuns().get(2)));
	}

	@Test
	public void placeholderSpansFullSingleRun() {
		ParagraphWrapper wrapper = loremIpsum();
		wrapper.replace("lorem", RunUtil.create(""));
		assertEquals(" ipsum", wrapper.getText());
	}

	@Test
	public void placeholderWithinSingleRun() {
		ParagraphWrapper wrapper = new ParagraphWrapper(create("My name is ${name}."));
		wrapper.replace("${name}", RunUtil.create("Bob"));
		assertEquals("My name is Bob.", wrapper.getText());
	}

	@Test
	public void placeholderAtStartOfSingleRun() {
		ParagraphWrapper wrapper = new ParagraphWrapper(create("${name} my name is."));
		wrapper.replace("${name}", RunUtil.create("Yoda"));
		assertEquals("Yoda my name is.", wrapper.getText());
	}

	@Test
	public void placeholderAtEndOfSingleRun() {
		ParagraphWrapper wrapper = new ParagraphWrapper(create("My name is ${name}"));
		wrapper.replace("${name}", RunUtil.create("Yoda"));
		assertEquals("My name is Yoda", wrapper.getText());
	}

	@Test
	public void placeholderWithinMultipleRuns() {
		ParagraphWrapper wrapper = new ParagraphWrapper(create("My name is ${", "name", "}."));
		wrapper.replace("${name}", RunUtil.create("Yoda"));
		assertEquals("My name is Yoda.", wrapper.getText());
	}

	@Test
	public void placeholderStartsWithinMultipleRuns() {
		ParagraphWrapper wrapper = new ParagraphWrapper(create("${", "name", "} my name is."));
		wrapper.replace("${name}", RunUtil.create("Yoda"));
		assertEquals("Yoda my name is.", wrapper.getText());
	}

	@Test
	public void placeholderEndsWithinMultipleRuns() {
		ParagraphWrapper wrapper = new ParagraphWrapper(create("My name is ${", "name", "}"));
		wrapper.replace("${name}", RunUtil.create("Yoda"));
		assertEquals("My name is Yoda", wrapper.getText());
	}

	@Test
	public void placeholderExactlySpansMultipleRuns() {
		ParagraphWrapper wrapper = new ParagraphWrapper(create("${", "name", "}"));
		wrapper.replace("${name}", RunUtil.create("Yoda"));
		assertEquals("Yoda", wrapper.getText());
	}
}