package pro.verron.officestamper.api;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.R;
import org.springframework.lang.Nullable;
import org.wickedsource.docxstamper.util.RunUtil;

/**
 * This is an abstract class that provides a generic implementation for
 * resolving objects to strings. It is used in conjunction with
 * {@link ObjectResolver} interface to provide a flexible way to
 * resolve different types of objects to strings.
 *
 * @param <T> the type of the object to resolve
 * @author Joseph Verron
 * @version ${version}
 * @since 1.6.7
 */
public abstract class StringResolver<T>
        implements ObjectResolver {

    private final Class<T> type;

    /**
     * Creates a new StringResolver with the given type.
     *
     * @param type the type of object to be resolved
     */
    protected StringResolver(Class<T> type) {
        assert type != null;
        this.type = type;
    }

    /**
     * Determines if the given object can be resolved by the StringResolver.
     *
     * @param object the object to be resolved
     * @return true if the object can be resolved, false otherwise
     */
    @Override
    public final boolean canResolve(@Nullable Object object) {
        return type.isInstance(object);
    }

    /**
     * Resolves an object to a string and creates a new run with the resolved string as content.
     *
     * @param document   the WordprocessingMLPackage document
     * @param expression the expression string
     * @param object     the object to be resolved
     * @return the newly created run with the resolved string as content
     */
    @Override
    public final R resolve(
            WordprocessingMLPackage document,
            String expression,
            Object object
    ) {
        return RunUtil.create(resolve(type.cast(object)));
    }

    /**
     * Resolves an object to a string.
     *
     * @param object the object to be resolved
     * @return the string representation of the object
     */
    protected abstract String resolve(T object);
}
