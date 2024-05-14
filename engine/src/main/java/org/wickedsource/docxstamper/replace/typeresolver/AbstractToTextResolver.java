package org.wickedsource.docxstamper.replace.typeresolver;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.R;
import org.wickedsource.docxstamper.util.RunUtil;

/**
 * Abstract ITypeResolver that takes a String from the implementing subclass and creates a Run of text
 * from it.
 *
 * @param <S> the type which to map into a run of text.
 * @author Joseph Verron
 * @author Tom Hombergs
 * @version ${version}
 * @since 1.0.0
 */
@Deprecated(since = "1.6.7", forRemoval = true)
public abstract class AbstractToTextResolver<S>
		implements pro.verron.officestamper.api.ITypeResolver<S> {

	/**
	 * Default constructor.
	 */
	protected AbstractToTextResolver() {
	}

	/**
	 * {@inheritDoc}
	 *
	 * Creates a Run of text from the resolved String.
	 */
	@Override
	public R resolve(WordprocessingMLPackage document, S expressionResult) {
		String text = resolveStringForObject(expressionResult);
		return RunUtil.create(text);
	}

	/**
	 * Resolves the String for the given object.
	 *
	 * @param object the object to resolve the String for.
	 * @return the String for the given object.
	 */
	protected abstract String resolveStringForObject(S object);
}
