import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.log4j.Logger;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;

public class JavaToTStransformer {
    private static final Logger logger = Logger.getLogger(JavaToTStransformer.class);

    public final static Set<String> validArgs = new HashSet<>(Arrays.asList("p", "i", "o", "sr", "tr", "fr"));
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
        JAVA_TS_TYPE_MATCHING.put("com.google.gson.JsonArray", "any[]");

    }

    private final Map<String, TypescriptType> visitedTypes = new HashMap<>();
    private static final String INDENTATION = "    ";
    private final Map<String, String> params;
    private Launcher launcher = new Launcher();
    public final boolean doIndent;
    public final String indentation;

    public void clear() {
        visitedTypes.clear();
    }

    public JavaToTStransformer(Map<String, String> params) {
        this.params = params;
        this.doIndent = true;
        this.indentation = INDENTATION;
    }

    public JavaToTStransformer(boolean doIndent) {
        this.doIndent = doIndent;
        this.indentation = doIndent ? INDENTATION : "";
        this.params = new HashMap<>();
    }

    public void parseType(CtType<?> type) {
        if (type == null || visitedTypes.containsKey(type.getQualifiedName())) {
            return;
        }
        visitedTypes.put(type.getQualifiedName(), null);
        TypescriptType res = null;
        if (type instanceof CtEnum) {
            res = parseEnum((CtEnum) type);
        } else if (type instanceof CtClass) {
            res = parseClass((CtClass) type);
        }
        visitedTypes.put(type.getQualifiedName(), res);
    }

    private TypescriptClass parseClass(CtClass cls) {
        final TypescriptClass tsClass = new TypescriptClass(cls.getSimpleName());

        if (cls.getSuperclass() != null) {
            tsClass.superClassName = cls.getSuperclass().getSimpleName();
            tsClass.imports.add(cls.getSuperclass().getDeclaration());
            parseType(cls.getSuperclass().getTypeDeclaration());
        }

        cls.getElements(f -> f instanceof CtField && !((CtField) f).hasModifier(ModifierKind.PRIVATE) && f.getParent() == cls).forEach(f -> {
            CtField field = (CtField) f;
            if (field.getType().getDeclaration() != null) {
                parseType(field.getType().getDeclaration());
            }
            for (CtTypeReference<?> actualTypeArgument : field.getType().getActualTypeArguments()) {
                if (actualTypeArgument.getDeclaration() != null) {
                    tsClass.imports.add(actualTypeArgument.getDeclaration());
                    parseType(actualTypeArgument.getDeclaration());
                }
            }
            tsClass.imports.add(field.getType().getDeclaration());
            tsClass.fields.add(new TypescriptField(getType(field.getType()), field.getSimpleName()));

        });
        tsClass.path = getOutPath(cls);
        return tsClass;
    }

    private String getOutPath(CtType type) {
        if (!params.containsKey("-tr")) {
            return null;
        }
        String fname = type.getPosition().getFile().getName().replaceFirst("[.][^.]+$", "").replaceAll("([A-Z]+)", "-$1").toLowerCase();
        if (fname.startsWith("-")) {
            fname = fname.substring(1);
        }
        String path = Paths.get(params.get("-tr")).resolve(type.getPosition().getFile().getParent().split(params.get("-sr"))[1]).resolve(fname + ".ts").toString();
        return path;
    }

    private TypescriptEnum parseEnum(CtEnum<?> member) {
        String name = member.getSimpleName();
        TypescriptEnum resEnum = new TypescriptEnum(name);
        for (CtEnumValue<?> ev : member.getEnumValues()) {
            String val = null;
            CtExpression<?> defaultExpression = ev.getDefaultExpression();

            if (defaultExpression instanceof CtConstructorCall) {
                List arguments = ((CtConstructorCall) defaultExpression).getArguments();

                if (!arguments.isEmpty() && (arguments.get(0) instanceof CtLiteral) && (!((CtLiteral) arguments.get(0)).getType().getQualifiedName().equals("boolean"))) {
                    val = arguments.get(0).toString();
                }
            }
            resEnum.fields.add(new TypescriptEnumField(ev.getSimpleName(), val));
        }
        resEnum.path = getOutPath(member);
        return resEnum;
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

    void parse() throws IOException {
        clear();
        CtClass startClass = findStartingClass(params.getOrDefault("-i", null));
        parseType(startClass);
    }

    private CtClass findStartingClass(String name) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String searchQuery = name;
        while (searchQuery == null) {
            System.out.println("------------------------------------------------------------------------------------\n");
            System.out.println("Enter java class qualified name:");
            searchQuery = br.readLine();
        }
        String finalSearchQuery = searchQuery;
        List<CtClass> elements = launcher.getModel().getElements(element -> element != null && element.getQualifiedName().contains(finalSearchQuery));
        CtClass startClass;
        if (elements.size() > 1) {
            int selectedOption = -1;
            while (selectedOption < 0) {
                System.out.println("\n\n");
                System.out.println("------------------------------------------------------------------------------------");
                System.out.println("Select element to translate:");
                for (int i = 0; i < elements.size(); i++) {
                    System.out.println(String.format("  [%d]: %s", i, elements.get(i).getQualifiedName()));
                }
                String input = "";
                input = br.readLine();
                if (!input.isEmpty()) {
                    selectedOption = Integer.parseInt(input);
                }
            }
            startClass = elements.get(selectedOption);
        } else if (elements.size() == 1) {
            startClass = elements.get(0);
        } else {
            String msg = String.format("Nothing found for: %s", searchQuery);
            logger.info(msg);
            throw new IllegalArgumentException(msg);
        }
        return startClass;
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> params = parseArgs(args);
        for (int i = 0; i < args.length; i++) {
            logger.debug(String.format("args[%d]=%s", i, args[i]));
        }
        if (!params.containsKey("-p")) {
            throw new IllegalArgumentException("Missing required parameter (-p) with source files classpath");
        }

        JavaToTStransformer javaToTStransformer = new JavaToTStransformer(params);
        javaToTStransformer.buildLauncher();

        do {
            try {
                run(javaToTStransformer, params);
            } catch (IllegalArgumentException e) {
                if (params.containsKey("-i")) {
                    throw e;
                }
            }
        } while (!params.containsKey("-i"));
    }

    private static void run(JavaToTStransformer javaToTStransformer, Map<String, String> params) throws IOException {
        javaToTStransformer.parse();

        if (params.containsKey("-tr")) {
            Map<String, List<TypescriptType>> byFilename = groupByFilename(javaToTStransformer);

            for (Map.Entry<String, List<TypescriptType>> entry : byFilename.entrySet()) {
                StringBuilder fileSb = new StringBuilder();
                Path path = Paths.get(entry.getKey());

                if (params.containsKey("-fr")) {
                    Stream<CtType> importType = entry.getValue().stream().filter(i -> i instanceof TypescriptClass)
                            .flatMap(l -> ((TypescriptClass) l).imports.stream())
                            .filter(e -> e != null && !path.toString().equals(javaToTStransformer.getOutPath(e)));
                    fileSb.append(javaToTStransformer.createImports(importType));
                    fileSb.append("\n\n");
                }

                List<String> types = entry.getValue().stream().map(Object::toString).collect(Collectors.toList());
                fileSb.append(String.join("\n", String.join("\n\n", types)).trim());


                logger.info(String.format("Writing to %s", path));
                if (Files.notExists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(path, ((Files.exists(path) ? "\n\n" : "") + fileSb.toString()).getBytes(), StandardOpenOption.APPEND);
            }
        } else {
            System.out.println("\n\n---------------------------------------------------------------------------\n" + javaToTStransformer.toString());
        }
    }

    public String toString() {
        return String.join("\n", visitedTypes.values().stream().map(Object::toString).collect(Collectors.toList())).trim();
    }

    private static Map<String, List<TypescriptType>> groupByFilename(JavaToTStransformer javaToTStransformer) {
        Map<String, List<TypescriptType>> byFilename = new HashMap<>();

        for (TypescriptType value : javaToTStransformer.visitedTypes.values()) {
            if (!byFilename.containsKey(value.path)) {
                byFilename.put(value.path, new ArrayList<>());
            }
            byFilename.get(value.path).add(value);
        }
        return byFilename;
    }

    private String createImports(Stream<CtType> type) {

        Set<String> collect = type.map(t -> {
            String fr = params.get("-fr");
            String tsFilePath = getOutPath(t);
            String tsRelativeToRoot = Paths.get(fr).relativize(Paths.get(tsFilePath)).toString().split("\\.ts")[0];
            return String.format("import {%s} from '@%s';", t.getSimpleName(), tsRelativeToRoot);
        }).collect(Collectors.toSet());
        return String.join("\n", collect);
    }

    private void buildLauncher() {
        for (String p : params.get("-p").split(":")) {
            launcher.addInputResource(p);
        }
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        logger.info("Starting to build source code model");
        launcher.buildModel();
        logger.info("Done building source code model");
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> res = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (isValidArg(arg)) {
                res.put(arg, isValidArg(args[i + 1]) ? null : args[i + 1]);
            }
        }
        return res;
    }

    private static boolean isValidArg(String arg) {
        return arg.startsWith("-") && validArgs.contains(arg.substring(1));
    }

    public abstract static class TypescriptType {
        String name;
        String qualifiedName;
        String path;

        public TypescriptType(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TypescriptType that = (TypescriptType) o;
            return Objects.equals(qualifiedName, that.qualifiedName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(qualifiedName);
        }
    }

    public class TypescriptEnum extends TypescriptType {
        final List<TypescriptEnumField> fields = new ArrayList<>();

        public TypescriptEnum(String name) {
            super(name);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(MessageFormat.format("export enum {0} '{' \n", name));

            for (int i = 0; i < this.fields.size(); i++) {
                TypescriptEnumField val = this.fields.get(i);
                sb.append(indentation + val.name + " = " + (val.value == null ? "\"" + val.name + "\"" : val.value));
                if (i != this.fields.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    public class TypescriptClass extends TypescriptType {
        public List<CtType> imports = new ArrayList<>();
        String superClassName;
        final List<TypescriptField> fields = new ArrayList<>();

        public TypescriptClass(String name) {
            super(name);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            String extendsClause = this.superClassName != null ? " extends " + this.superClassName : "";
            sb.append(MessageFormat.format("export interface {0}{1} '{'\n", this.name, extendsClause));
            for (TypescriptField field : this.fields) {
                sb.append(MessageFormat.format(indentation + "{0}: {1};\n", field.name, field.clazz));
            }
            sb.append("}\n");

            return sb.toString();
        }
    }

    public static class TypescriptEnumField {
        String name;
        String value;

        public TypescriptEnumField(String name) {
            this.name = name;
        }

        public TypescriptEnumField(String name, String value) {
            this.name = name;
            this.value = value;
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
}
