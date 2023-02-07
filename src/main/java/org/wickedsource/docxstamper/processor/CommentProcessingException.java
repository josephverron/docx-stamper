package org.wickedsource.docxstamper.processor;

import org.docx4j.wml.P;
import org.wickedsource.docxstamper.api.DocxStamperException;
import org.wickedsource.docxstamper.api.coordinates.ParagraphCoordinates;
import org.wickedsource.docxstamper.api.coordinates.TableCoordinates;

import static org.docx4j.TextUtils.getText;

public class CommentProcessingException extends DocxStamperException {

    public CommentProcessingException(String message, P paragraph) {
        super(message + "\nCoordinates of offending commented paragraph within the document: \n" + getText(paragraph));
    }

    public CommentProcessingException(String message, ParagraphCoordinates coordinates) {
        super(message + "\nCoordinates of offending commented paragraph within the document: \n" + coordinates.toString());
    }

    public CommentProcessingException(String message, TableCoordinates coordinates) {
        super(message + "\nCoordinates of offending commented table within the document: \n" + coordinates.toString());
    }

}
