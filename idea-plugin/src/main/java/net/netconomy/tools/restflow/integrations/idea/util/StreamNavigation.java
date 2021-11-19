package net.netconomy.tools.restflow.integrations.idea.util;

import java.util.function.BinaryOperator;
import java.util.function.Predicate;


public class StreamNavigation {

    private StreamNavigation() {
    }

    public static Predicate<Object> before(Object ref) {
        return new Predicate<Object>() {
            private boolean found = false;

            @Override
            public boolean test(Object elem) {
                if (found) {
                    return false;
                } else {
                    found = elem.equals(ref);
                    return !found;
                }
            }
        };
    }

    public static Predicate<Object> after(Object ref) {
        return new Predicate<Object>() {
            private boolean found = false;

            @Override
            public boolean test(Object node) {
                if (found) {
                    return true;
                } else {
                    found = node.equals(ref);
                    return false;
                }
            }
        };
    }

    public static <T> BinaryOperator<T> last() {
        return (l, r) -> r;
    }
}
