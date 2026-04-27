package gen;

public final class TestConfigGenerated {
    private static boolean installed;

    private TestConfigGenerated() {
    }

    public static void install() {
        installed = true;
    }

    public static void reset() {
        installed = false;
    }

    public static boolean installed() {
        return installed;
    }
}
