package pro.verron.officestamper.test;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.lang.NonNull;

/**
 * @author Joseph Verron
 * @version ${version}
 * @since 1.6.6
 */
public class SimpleGetter implements PropertyAccessor {

    private final String fieldName;

    private final Object value;

    /**
     * <p>Constructor for SimpleGetter.</p>
     *
     * @param fieldName a {@link java.lang.String} object
     * @param value     a {@link java.lang.Object} object
     */
    public SimpleGetter(String fieldName, Object value) {
        this.fieldName = fieldName;
        this.value = value;
    }

    /** {@inheritDoc} */
    @Override
    public Class<?>[] getSpecificTargetClasses() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canRead(
            @NonNull EvaluationContext context,
            Object target,
            @NonNull String name
    ) {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public TypedValue read(
            @NonNull EvaluationContext context, Object target, String name
    ) {
        if (name.equals(this.fieldName)) {
            return new TypedValue(value);
        } else {
            return TypedValue.NULL;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean canWrite(
            @NonNull EvaluationContext context,
            Object target,
            @NonNull String name
    ) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void write(
            @NonNull EvaluationContext context,
            Object target,
            @NonNull String name,
            Object newValue
    ) {
    }
}
