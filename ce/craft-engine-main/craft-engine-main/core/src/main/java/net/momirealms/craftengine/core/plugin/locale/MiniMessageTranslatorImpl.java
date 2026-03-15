package net.momirealms.craftengine.core.plugin.locale;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.translation.Translator;
import net.kyori.adventure.util.TriState;
import net.kyori.examination.ExaminableProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.stream.Stream;

public class MiniMessageTranslatorImpl implements MiniMessageTranslator {
    private static final Key NAME = Key.key(net.momirealms.craftengine.core.util.Key.DEFAULT_NAMESPACE, "main");
    static final MiniMessageTranslatorImpl INSTANCE = new MiniMessageTranslatorImpl();
    protected final TranslatableComponentRenderer<Locale> renderer = TranslatableComponentRenderer.usingTranslationSource(this);
    private Translator source;

    @Override
    public @NotNull Key name() {
        return NAME;
    }

    @Override
    public @NotNull TriState hasAnyTranslations() {
        if (this.source != null) {
            return TriState.TRUE;
        }
        return TriState.FALSE;
    }

    @Override
    public @Nullable MessageFormat translate(@NotNull String key, @NotNull Locale locale) {
        // No need to implement this method
        return null;
    }

    @Override
    public @Nullable Component translate(@NotNull TranslatableComponent component, @NotNull Locale locale) {
        if (this.source != null) {
            return this.source.translate(component, locale);
        }
        return null;
    }

    @Override
    public boolean setSource(@NotNull Translator source) {
        this.source = source;
        return true;
    }

    @Override
    public @NotNull Stream<? extends ExaminableProperty> examinableProperties() {
        return Stream.of(ExaminableProperty.of("source", this.source));
    }
}
