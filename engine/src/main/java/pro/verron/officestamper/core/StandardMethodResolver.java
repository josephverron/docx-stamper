package pro.verron.officestamper.core;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.*;
import org.springframework.lang.NonNull;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves methods that are used as expression functions or comment processors.
 *
 * @author Joseph Verron
 * @version ${version}
 * @since 1.6.2
 */
public class StandardMethodResolver
        implements MethodResolver {
    private final Map<Class<?>, Object> commentProcessors;
    private final Map<Class<?>, Object> expressionFunctions;

    /**
     * <p>Constructor for StandardMethodResolver.</p>
     *
     * @param commentProcessors   map of comment processors
     * @param expressionFunctions map of expression functions
     */
    public StandardMethodResolver(
            Map<Class<?>, Object> commentProcessors,
            Map<Class<?>, Object> expressionFunctions
    ) {
        this.commentProcessors = commentProcessors;
        this.expressionFunctions = expressionFunctions;
    }

    /** {@inheritDoc} */
    @Override
    public MethodExecutor resolve(
            @NonNull EvaluationContext context,
            @NonNull Object targetObject,
            @NonNull String name,
            @NonNull List<TypeDescriptor> argumentTypes
    ) {
        return findCommentProcessorMethod(name, argumentTypes)
                .or(() -> findExpressionContextMethod(name, argumentTypes))
                .orElse(null);
    }

    private Optional<Invoker> findCommentProcessorMethod(String expectedName, List<TypeDescriptor> expectedArguments) {
        return findMethodInMap(commentProcessors, expectedName, expectedArguments);
    }

    private Optional<Invoker> findExpressionContextMethod(String expectedName, List<TypeDescriptor> expectedArguments) {
        return findMethodInMap(expressionFunctions, expectedName, expectedArguments);
    }

    private Optional<Invoker> findMethodInMap(
            Map<Class<?>, Object> methodMap,
            String expectedName,
            List<TypeDescriptor> expectedArguments
    ) {
        for (Map.Entry<Class<?>, Object> entry : methodMap.entrySet()) {
            Class<?> iface = entry.getKey();
            for (Method actualMethod : iface.getDeclaredMethods())
                if (methodEquals(actualMethod, expectedName, expectedArguments))
                    return Optional.of(new Invoker(entry.getValue(), actualMethod));
        }
        return Optional.empty();
    }

    private boolean methodEquals(Method actualMethod, String expectedName, List<TypeDescriptor> expectedArguments) {
        var actualMethodName = actualMethod.getName();
        if (!actualMethodName.equals(expectedName))
            return false;

        var actualMethodParameterTypes = actualMethod.getParameterTypes();
        if (actualMethodParameterTypes.length != expectedArguments.size())
            return false;

        for (int i = 0; i < expectedArguments.size(); i++) {
            var expectedArgument = expectedArguments.get(i);
            if (expectedArgument == null) return false;
            Class<?> expectedType = expectedArgument.getType();
            Class<?> actualType = actualMethodParameterTypes[i];
            if (!actualType.isAssignableFrom(expectedType))
                return false;
        }
        return true;
    }

}
