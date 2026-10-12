import app.msime.android.KeyboardArrangementPolicy;
import app.msime.android.KeyboardLayout;
import java.util.HashSet;
import java.util.List;

public final class KeyboardArrangementPolicySmoke {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        for (String preset : List.of("phone", "chat", "calculator", "row", "custom")) {
            var layout = KeyboardLayout.numberLayer(true, true, preset, "9876543210");
            var digits = new HashSet<String>();
            var kinds = new HashSet<KeyboardLayout.LayerKeyKind>();
            for (var row : layout) for (var key : row) {
                kinds.add(key.kind());
                if (key.text().matches("[0-9]")) check(digits.add(key.text()));
            }
            check(digits.size() == 10);
            for (var kind : List.of(KeyboardLayout.LayerKeyKind.DELETE, KeyboardLayout.LayerKeyKind.LETTERS,
                    KeyboardLayout.LayerKeyKind.SPACE, KeyboardLayout.LayerKeyKind.RETURN, KeyboardLayout.LayerKeyKind.LAYER_TOGGLE))
                check(kinds.contains(kind));
        }
        check(!KeyboardArrangementPolicy.validDigits("1123456789"));
        check(!KeyboardArrangementPolicy.validDigits("012345678x"));
        check(KeyboardArrangementPolicy.digits("calculator", "").equals("7894561230"));
        check(KeyboardArrangementPolicy.symbolRows("! @ # $\n+ - / =") != null);
        check(KeyboardArrangementPolicy.symbolRows("! ! # $\n+ - / =") == null);
        check(KeyboardArrangementPolicy.symbolRows("! @ #\n+ - / =") == null);
        var custom = KeyboardLayout.moreSymbolLayer(true, true, "! @ # $\n+ - / =");
        check(custom.get(0).get(0).text().equals("!"));
        check(custom.get(1).get(3).text().equals("="));
        System.out.println("Keyboard arrangement presets passed");
    }
}
