package org.wickedsource.docxstamper;

import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.wickedsource.docxstamper.api.DocxStamperException;
import org.wickedsource.docxstamper.api.commentprocessor.ICommentProcessor;
import org.wickedsource.docxstamper.api.typeresolver.ITypeResolver;
import org.wickedsource.docxstamper.api.typeresolver.TypeResolverRegistry;
import org.wickedsource.docxstamper.el.ExpressionResolver;
import org.wickedsource.docxstamper.processor.CommentProcessorRegistry;
import org.wickedsource.docxstamper.replace.PlaceholderReplacer;
import org.wickedsource.docxstamper.replace.typeresolver.DateResolver;
import org.wickedsource.docxstamper.replace.typeresolver.FallbackResolver;
import org.wickedsource.docxstamper.replace.typeresolver.image.Image;
import org.wickedsource.docxstamper.replace.typeresolver.image.ImageResolver;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;

/**
 * <p>
 * Main class of the docx-stamper library. This class can be used to "stamp" .docx templates
 * to create a .docx document filled with custom data at runtime.
 * </p>
 *
 * @param <T> the class of the context object used to resolve expressions against.
 */
public class DocxStamper<T> {

	private PlaceholderReplacer placeholderReplacer;

	private CommentProcessorRegistry commentProcessorRegistry;

	private TypeResolverRegistry typeResolverRegistry;

	private DocxStamperConfiguration config = new DocxStamperConfiguration();

	public DocxStamper() {
		initFields();
	}

	private void initFields() {
		typeResolverRegistry = new TypeResolverRegistry(new FallbackResolver());
		typeResolverRegistry.registerTypeResolver(Image.class, new ImageResolver());
		typeResolverRegistry.registerTypeResolver(Date.class, new DateResolver("dd.MM.yyyy"));
		for (Map.Entry<Class<?>, ITypeResolver> entry : config.getTypeResolvers().entrySet()) {
			typeResolverRegistry.registerTypeResolver(entry.getKey(), entry.getValue());
		}

		ExpressionResolver expressionResolver = new ExpressionResolver(config);
		placeholderReplacer = new PlaceholderReplacer(typeResolverRegistry, config);

		config.getCommentProcessorsToUse()
			  .forEach((key, processor) -> config.putCommentProcessor(key, tryInstantiate(processor)));

		commentProcessorRegistry = new CommentProcessorRegistry(placeholderReplacer, config);
		commentProcessorRegistry.setExpressionResolver(expressionResolver);
	}

	private Object tryInstantiate(Class<?> processor) {
		try {
			return instantiate(processor);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private Object instantiate(Class<?> processor) throws ReflectiveOperationException {
		var constructor = processor.getDeclaredConstructor(DocxStamperConfiguration.class, TypeResolverRegistry.class);
		return constructor.newInstance(config, typeResolverRegistry);
	}

	public DocxStamper(DocxStamperConfiguration config) {
		this.config = config;
		initFields();
	}

	/**
	 * <p>
	 * Reads in a .docx template and "stamps" it into the given OutputStream, using the specified context object to
	 * fill out any expressions it finds.
	 * </p>
	 * <p>
	 * In the .docx template you have the following options to influence the "stamping" process:
	 * </p>
	 * <ul>
	 * <li>Use expressions like ${name} or ${person.isOlderThan(18)} in the template's text. These expressions are resolved
	 * against the contextRoot object you pass into this method and are replaced by the results.</li>
	 * <li>Use comments within the .docx template to mark certain paragraphs to be manipulated. </li>
	 * </ul>
	 * <p>
	 * Within comments, you can put expressions in which you can use the following methods by default:
	 * </p>
	 * <ul>
	 * <li><em>displayParagraphIf(boolean)</em> to conditionally display paragraphs or not</li>
	 * <li><em>displayTableRowIf(boolean)</em> to conditionally display table rows or not</li>
	 * <li><em>displayTableIf(boolean)</em> to conditionally display whole tables or not</li>
	 * <li><em>repeatTableRow(List&lt;Object&gt;)</em> to create a new table row for each object in the list and resolve expressions
	 * within the table cells against one of the objects within the list.</li>
	 * </ul>
	 * <p>
	 * If you need a wider vocabulary of methods available in the comments, you can create your own ICommentProcessor
	 * and register it via getCommentProcessorRegistry().addCommentProcessor().
	 * </p>
	 *
	 * @param template    the .docx template.
	 * @param contextRoot the context root object against which all expressions found in the template are evaluated.
	 * @param out         the output stream in which to write the resulting .docx document.
	 * @throws DocxStamperException in case of an error.
	 */
	public void stamp(InputStream template, T contextRoot, OutputStream out) throws DocxStamperException {
		try {
			WordprocessingMLPackage document = WordprocessingMLPackage.load(template);
			stamp(document, contextRoot, out);
		} catch (Docx4JException e) {
			throw new DocxStamperException(e);
		}
	}

	/**
	 * Same as stamp(InputStream, T, OutputStream) except that you may pass in a DOCX4J document as a template instead
	 * of an InputStream.
	 *
	 * @param document    the .docx template.
	 * @param contextRoot the context root object against which all expressions found in the template are evaluated.
	 * @param out         the output stream in which to write the resulting .docx document.
	 * @throws DocxStamperException in case of an error.
	 */
	public void stamp(WordprocessingMLPackage document, T contextRoot, OutputStream out) throws DocxStamperException {
		try {
			processComments(document, contextRoot);
			replaceExpressions(document, contextRoot);
			document.save(out);
			commentProcessorRegistry.reset();
		} catch (Docx4JException e) {
			throw new DocxStamperException(e);
		}
	}

	private void processComments(final WordprocessingMLPackage document, T contextObject) {
		commentProcessorRegistry.runProcessors(document, contextObject);
	}

	private void replaceExpressions(WordprocessingMLPackage document, T contextObject) {
		placeholderReplacer.resolveExpressions(document, contextObject);
	}

	/**
	 * This method allows getting comment processors instances in use to access their internal state. Useful for
	 * testing purposes.
	 *
	 * @param interfaceToGet ICommentProcessor interface to lookup.
	 * @return ICommentProcessor implementation instance or null if not used.
	 */
	public ICommentProcessor getCommentProcessorInstance(Class<?> interfaceToGet) {
		return (ICommentProcessor) config.getCommentProcessors().get(interfaceToGet);
	}
}
