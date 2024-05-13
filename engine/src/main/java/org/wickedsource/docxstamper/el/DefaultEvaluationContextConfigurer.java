package org.wickedsource.docxstamper.el;

import org.springframework.expression.TypeLocator;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.*;
import org.wickedsource.docxstamper.api.EvaluationContextConfigurer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link EvaluationContextConfigurer} that has better default security,
 * especially doesn't allow especially known injections.
 *
 * @author Joseph Verron
 * @version ${version}
 * @since 1.6.5
 */
public class DefaultEvaluationContextConfigurer implements EvaluationContextConfigurer {
    /**
     * {@inheritDoc}
     */
    @Override
    public void configureEvaluationContext(StandardEvaluationContext context) {
        TypeLocator typeLocator = typeName -> {
            throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
        };
        context.setPropertyAccessors(List.of(DataBindingPropertyAccessor.forReadWriteAccess()));
        context.setConstructorResolvers(Collections.emptyList());
        context.setMethodResolvers(new ArrayList<>(List.of(DataBindingMethodResolver.forInstanceMethodInvocation())));
        //noinspection DataFlowIssue, ignore the warning since it is a workaround fixing potential security issues
        context.setBeanResolver(null);
        context.setTypeLocator(typeLocator);
        context.setTypeConverter(new StandardTypeConverter());
        context.setTypeComparator(new StandardTypeComparator());
        context.setOperatorOverloader(new StandardOperatorOverloader());
    }
}
