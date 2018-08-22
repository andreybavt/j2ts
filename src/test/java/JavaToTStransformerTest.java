import static org.junit.jupiter.api.Assertions.*;

class JavaToTStransformerTest {

    @org.junit.jupiter.api.Test
    void parsePrivatePublic() {
        String java = String.format("class Test {\n    private String priv;\n    public String pub;\n}");
        String result = "export enum TestEnum { \nA,\nB,\nC\n}";
        assertEquals(result, new JavaToTStransformer(false).parse(java));
    }

    @org.junit.jupiter.api.Test
    void parseEnum() {
        String java = "enum TestEnum {\n    A, B, C\n}";
        String result = "export enum TestEnum { \nA,\nB,\nC\n}";
        assertEquals(result, new JavaToTStransformer(false).parse(java));
    }

    @org.junit.jupiter.api.Test
    void parseEnumWithValues() {
        String java = "public enum KNNAlgorithm {\n" +
                "    AUTO (\"auto\"),\n" +
                "    KD_TREE (\"kd_tree\"),\n" +
                "    BALL_TREE (\"ball_tree\"),\n" +
                "    BRUTE (\"brute\");\n" +
                "\n" +
                "    private final String name;\n" +
                "    KNNAlgorithm(String s){\n" +
                "        this.name = s;\n" +
                "    }\n" +
                "}";
        String result = "export enum KNNAlgorithm { \n" +
                "AUTO = \"auto\",\n" +
                "KD_TREE = \"kd_tree\",\n" +
                "BALL_TREE = \"ball_tree\",\n" +
                "BRUTE = \"brute\"\n" +
                "}";
        assertEquals(result, new JavaToTStransformer(false).parse(java));
    }

    @org.junit.jupiter.api.Test
    void parseBasicClasses() {
        String content = "class Scratch {\n" +
                "    public byte aByte;\n" +
                "    public int anInt;\n" +
                "    public long aLong;\n" +
                "    public float aFloat;\n" +
                "    public double aDouble;\n" +
                "    public char aChar;\n" +
                "    public Byte aByteW;\n" +
                "    public Integer integer;\n" +
                "    public Float aFloatW;\n" +
                "    public Long aLongW;\n" +
                "    public String string;\n" +
                "}";
        String result = "export interface Scratch {\n" +
                "aByte: number;\n" +
                "anInt: number;\n" +
                "aLong: number;\n" +
                "aFloat: number;\n" +
                "aDouble: number;\n" +
                "aChar: string;\n" +
                "aByteW: number;\n" +
                "integer: number;\n" +
                "aFloatW: number;\n" +
                "aLongW: number;\n" +
                "string: string;\n" +
                "}";
        assertEquals(result, new JavaToTStransformer(false).parse(content));
    }
}