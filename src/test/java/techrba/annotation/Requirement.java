package techrba.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a test method to one or more business requirements, enabling automatic
 * traceability between automated checks and the requirements traceability
 * matrix (RTM).
 *
 * <p>Idioms used in this project (see docs/traceability-matrix.md):
 * <ul>
 *   <li>{@code P1..P4} - Postman / REST requirements</li>
 *   <li>{@code S1..S8} - Selenium UI requirements</li>
 *   <li>{@code U1..U3} - unit-converter requirements</li>
 * </ul>
 * Example: {@code @Requirement({"S2", "S4"})}</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Requirement {

    /** One or more requirement identifiers this test covers. */
    String[] value() default {};
}
