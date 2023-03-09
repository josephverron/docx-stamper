package org.wickedsource.docxstamper;

import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.wml.P;
import org.junit.jupiter.api.Test;
import org.wickedsource.docxstamper.context.DateContext;
import org.wickedsource.docxstamper.util.ParagraphWrapper;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateReplacementTest {
	@Test
	public void test() throws Docx4JException, IOException {
		var now = new Date();
		var context = new DateContext();
		context.setDate(now);

		var template = getClass().getResourceAsStream("DateReplacementTest.docx");
		var stamper = new TestDocxStamper<>();
		var document = stamper.stampAndLoad(template, context);

		var p = new ParagraphWrapper(((P) document.getMainDocumentPart().getContent().get(1)));

		assertEquals("Today is: " + new SimpleDateFormat("dd.MM.yyyy").format(now), p.getText());
	}
}
