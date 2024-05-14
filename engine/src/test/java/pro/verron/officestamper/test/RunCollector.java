package pro.verron.officestamper.test;

import org.docx4j.utils.TraversalUtilVisitor;
import org.docx4j.wml.R;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * <p>RunCollector class.</p>
 *
 * @since 1.6.5
 * @author Joseph Verron
 * @version ${version}
 */
public class RunCollector extends TraversalUtilVisitor<R> {
	private final Set<R> paragraphs = new LinkedHashSet<>();

	/**
	 * <p>runs.</p>
	 *
	 * @return a {@link java.util.stream.Stream} object
	 * @since 1.6.6
	 */
	public Stream<R> runs() {
		return paragraphs.stream();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void apply(R paragraph) {
		paragraphs.add(paragraph);
	}
}
