package util;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IValue;

public class IValueUtils {

    public static boolean isLexical(IValue value) {
        if (!(value instanceof IConstructor)) return false;
        String name = ((IConstructor) value).getName();
        return name.equals("lex") || name.equals("parameterized-lex");
    }

    public static boolean isLiteral(IValue value) {
        if (!(value instanceof IConstructor)) return false;
        return ((IConstructor) value).getName().equals("lit");
    }

    public static boolean isLayout(IValue value) {
        if (!(value instanceof IConstructor)) return false;
        return ((IConstructor) value).getName().equals("layouts");
    }

    public static boolean isStart(IValue value) {
        if (!(value instanceof IConstructor)) return false;
        return ((IConstructor) value).getName().equals("start");
    }
}
