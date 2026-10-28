package cn.dancingsnow.neoecoae.platform.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.widgets.ButtonWidget;

/** MUI2's button theme paints vanilla's hovered button over any hitbox whose hover background is left unset. */
class NativeUiHoverStyleTest {

    private static final Path UI = Paths.get("src/main/java/cn/dancingsnow/neoecoae/platform/ui");
    private static final Pattern BUTTON_SUBCLASS = Pattern
        .compile("class (\\w+) extends (?:com\\.cleanroommc\\.modularui\\.widgets\\.)?ButtonWidget<\\1>");

    @Test
    void plainHoverKeepsTheNormalBackgroundInsteadOfTheThemeHoverSprite() {
        Probe bare = NativeUiStyle.plainHover(new Probe());
        assertSame(IDrawable.EMPTY, bare.getBackground());
        assertSame(IDrawable.NONE, bare.getHoverBackground());

        IDrawable track = IDrawable.of(IDrawable.EMPTY, IDrawable.EMPTY);
        Probe textured = NativeUiStyle.plainHover(new Probe().background(track));
        assertSame(track, textured.getBackground());
        assertSame(IDrawable.NONE, textured.getHoverBackground());
    }

    @Test
    void scrollHitboxesUseTheSharedPlainHoverStyle() throws IOException {
        assertPlainHover("computation/NativeCpuPages.java", "CpuScrollbar");
        assertPlainHover("workstation/NativeLargeWorkstationPages.java", "QueueScrollbar");
        assertPlainHover("workstation/NativeLargeWorkstationPages.java", "Tasks");
        assertPlainHover("crafting/NativeCraftingInterfacePages.java", "Scrollbar");
    }

    @Test
    void everyDirectButtonSubclassOptsOutOfTheThemeHoverBackground() throws IOException {
        List<String> offenders = new ArrayList<>();
        List<Path> sources;
        try (Stream<Path> files = Files.walk(UI)) {
            sources = files.filter(
                path -> path.toString()
                    .endsWith(".java"))
                .collect(Collectors.toList());
        }
        for (Path source : sources) {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            Matcher matcher = BUTTON_SUBCLASS.matcher(text);
            while (matcher.find()) {
                String body = classBody(text, matcher.end());
                if (!body.contains("plainHover(this)") && !body.contains("hoverBackground(")
                    && !body.contains("disableHoverBackground()")
                    && !body.contains("void drawBackground("))
                    offenders.add(source.getFileName() + "#" + matcher.group(1));
            }
        }
        assertEquals(new ArrayList<String>(), offenders);
    }

    private static void assertPlainHover(String file, String type) throws IOException {
        String text = new String(Files.readAllBytes(UI.resolve(file)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("class " + type + " extends ButtonWidget<" + type + ">")
            .matcher(text);
        assertTrue(matcher.find(), type);
        assertTrue(
            classBody(text, matcher.end()).contains("NativeUiStyle.plainHover(this);"),
            type + " must not fall back to MUI2's hovered button sprite");
    }

    private static String classBody(String text, int from) {
        int open = text.indexOf('{', from), depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return text.substring(open, i + 1);
        }
        throw new IllegalStateException("unbalanced class body");
    }

    private static final class Probe extends ButtonWidget<Probe> {
    }
}
