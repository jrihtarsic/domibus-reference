package eu.domibus.web.rest.validators;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for custom validator that checks that the value does not contain any char from the blacklist
 *
 * @author Ion Perpegel
 * since 4.1
 */
@Target({TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = ObjectBlacklistValidator.class)
@Documented
public @interface ObjectWhiteListed {

    static String MESSAGE = "Forbidden character detected in property: %s ";

    String message() default MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
