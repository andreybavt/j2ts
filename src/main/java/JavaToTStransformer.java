import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;

public class JavaToTStransformer {
    private static final Logger logger = Logger.getLogger(JavaToTStransformer.class);

    private static final String INDENTATION = "\t\t";

    public final boolean doIndent;
    public final String indentation;


    public JavaToTStransformer() {
        doIndent = true;
        indentation = INDENTATION;
    }

    public JavaToTStransformer(boolean doIndent) {
        this.doIndent = doIndent;
        this.indentation = doIndent ? INDENTATION : "";
    }

    private static final Map<String, String> JAVA_TS_TYPE_MATCHING = new HashMap<>();

    static {
        JAVA_TS_TYPE_MATCHING.put("java.lang.String", "string");
        JAVA_TS_TYPE_MATCHING.put("java.lang.Character", "string");
        JAVA_TS_TYPE_MATCHING.put("char", "string");
        JAVA_TS_TYPE_MATCHING.put("byte", "number");
        JAVA_TS_TYPE_MATCHING.put("java.lang.Byte", "number");
        JAVA_TS_TYPE_MATCHING.put("int", "number");
        JAVA_TS_TYPE_MATCHING.put("java.lang.Integer", "number");
        JAVA_TS_TYPE_MATCHING.put("float", "number");
        JAVA_TS_TYPE_MATCHING.put("java.lang.Float", "number");
        JAVA_TS_TYPE_MATCHING.put("double", "number");
        JAVA_TS_TYPE_MATCHING.put("java.lang.Double", "number");
        JAVA_TS_TYPE_MATCHING.put("long", "number");
        JAVA_TS_TYPE_MATCHING.put("java.lang.Long", "number");
        JAVA_TS_TYPE_MATCHING.put("boolean", "boolean");
        JAVA_TS_TYPE_MATCHING.put("java.lang.Boolean", "boolean");
        JAVA_TS_TYPE_MATCHING.put("com.google.gson.JsonObject", "Dict<string>");

    }

    private final Map<String, String> classes = new HashMap<>();

    public static class TypescriptClass {
        final String name;
        String superClassName;
        final List<TypescriptField> fields = new ArrayList<>();

        TypescriptClass(String name) {
            this.name = name;
        }
    }

    public static class TypescriptField {
        final String clazz;
        final String name;

        TypescriptField(String clazz, String name) {
            this.clazz = clazz;
            this.name = name;
        }
    }

    private void parseClass(CtClass<?> cls) {
        String className = cls.getSimpleName();
        if (classes.containsKey(className)) {
            return;
        }
        if (cls instanceof CtEnum) {
            parseEnum((CtEnum) cls);
            return;
        }

        TypescriptClass tsClass = new TypescriptClass(className);
        if (cls.getSuperclass() != null) {
            tsClass.superClassName = cls.getSuperclass().getSimpleName();
        }

        for (CtTypeMember member : cls.getTypeMembers()) {
            if (member instanceof CtField && !member.getModifiers().contains(ModifierKind.PRIVATE)) {
                String fieldName = member.getSimpleName();
                tsClass.fields.add(new TypescriptField(getType(((CtField) member).getType()), fieldName));
            } else if (member instanceof CtEnum) {
                parseEnum((CtEnum) member);
            } else if (member instanceof CtClass) {
                parseClass((CtClass) member);
            } else {
                logger.debug(String.format("skipped member %s#%s (%s)", cls.getQualifiedName(), member.getSimpleName(), member.getClass().toString()));
            }
        }
        classes.put(className, toString(tsClass));

    }

    private void parseEnum(CtEnum<?> member) {
        String name = member.getSimpleName();
        StringBuilder sb = new StringBuilder();
        sb.append(MessageFormat.format("export enum {0} '{' \n", name));
        List<CtEnumValue<?>> enumValues = member.getEnumValues();
        for (int i = 0; i < enumValues.size(); i++) {
            CtEnumValue<?> ev = enumValues.get(i);
            sb.append(indentation + ev.getSimpleName());

            CtExpression<?> defaultExpression = ev.getDefaultExpression();
            if (defaultExpression instanceof CtConstructorCall) {
                List arguments = ((CtConstructorCall) defaultExpression).getArguments();
                sb.append(" = ");
                if (!arguments.isEmpty() && arguments.get(0) instanceof CtLiteral) {
                    sb.append(arguments.get(0));
                } else {
                    sb.append("\"" + ev.getSimpleName() + "\"");
                }
            }
            if (i != enumValues.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}");
        classes.put(name, sb.toString());
    }

    private String getType(CtTypeReference<?> type) {
        if (type instanceof CtArrayTypeReference) {
            return getType(((CtArrayTypeReferenceImpl) type).getComponentType()) + "[]";
        } else if (type instanceof CtTypeReference) {
            if ("java.util.Set".equals(type.getQualifiedName())) {
                return MessageFormat.format("Set<{0}>", getType(type.getActualTypeArguments().get(0)));
            }
            if ("java.util.List".equals(type.getQualifiedName()) || "java.util.Collection".equals(type.getQualifiedName())) {
                return getType(type.getActualTypeArguments().get(0)) + "[]";
            }
            if ("java.util.Map".equals(type.getQualifiedName())) {
                return MessageFormat.format("Map<{0}, {1}>", getType(type.getActualTypeArguments().get(0)), getType(type.getActualTypeArguments().get(1)));
            }

            if (JAVA_TS_TYPE_MATCHING.containsKey(type.getQualifiedName())) {
                return JAVA_TS_TYPE_MATCHING.get(type.getQualifiedName());
            }
            return type.getSimpleName();
        } else {
            String msg = "Should not have reached here ... " + type.toString();
            logger.error(msg);
            throw new RuntimeException(msg);
        }
    }


    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing required parameter with java input file path");
        }
        for (int i = 0; i < args.length; i++) {
            logger.debug(String.format("args[%d]=%s", i, args[i]));
        }
        byte[] byteContent = Files.readAllBytes(Paths.get(args[0]));

        String res = new JavaToTStransformer().parse(new String(byteContent));
        if (args.length > 1) {
            String outPath = args[1];

            if (Files.isDirectory(Paths.get(outPath))) {
                String fname = Paths.get(args[0]).getFileName().toString().replaceFirst("[.][^.]+$", "");
                fname = fname.replaceAll("([A-Z])", "-$1").toLowerCase();
                if (fname.startsWith("-")) {
                    fname = fname.substring(1);
                }
                outPath = Paths.get(outPath, fname + ".ts").toString();
            }
            logger.info(String.format("Writing result to %s", outPath));
            Files.write(Paths.get(outPath).toAbsolutePath(), res.getBytes());
        } else {
            System.out.println("\n\n---------------------------------------------------------------------------\n" + res);
        }
    }

    String parse(String content) {
        parseClass(Launcher.parseClass(content));
        return String.join("\n\n", classes.values()).trim();
    }

    private String toString(TypescriptClass tsClass) {
        StringBuilder sb = new StringBuilder();
        String extendsClause = tsClass.superClassName != null ? " extends " + tsClass.superClassName : "";
        sb.append(MessageFormat.format("export interface {0}{1} '{'\n", tsClass.name, extendsClause));
        for (TypescriptField field : tsClass.fields) {
            sb.append(MessageFormat.format(indentation + "{0}: {1};\n", field.name, field.clazz));
        }
        sb.append("}\n");

        return sb.toString();
    }
}
