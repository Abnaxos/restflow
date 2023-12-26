package net.netconomy.tools.restflow.integrations.idea.console;

import java.util.Map;

import javax.swing.Icon;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RfColorSettingsPage implements ColorSettingsPage {

    private final Map<String, TextAttributesKey> ADDITIONAL_ATTRS = ImmutableMap.<String, TextAttributesKey>builder()
            .put("consoleMsg", RfConsoleView.TA_CONSOLE_KEY)
            .put("httpOut", RfConsoleView.TA_HTTP_OUT_KEY)
            .put("httpInOk", RfConsoleView.TA_HTTP_IN_OK_KEY)
            .put("httpInErr", RfConsoleView.TA_HTTP_IN_ERR_KEY)
            .put("httpInWarn", RfConsoleView.TA_HTTP_IN_WARN_KEY)
            .build();

    @Nullable
    @Override
    public Icon getIcon() {
        // TODO (2019-08-24) add icon
        return null;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new PlainSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return """
          <consoleMsg>RESTflow Console Read</consoleMsg>
          <httpOut>GET /foo/bar</httpOut>
          <httpInOk>200 OK</httpInOk>
          <httpInErr>404 NOT_FOUND</httpInErr>
          <httpInWarn>301 MOVED_PERMANENTLY</httpInWarn>
          """;
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return ADDITIONAL_ATTRS;
    }

    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return new AttributesDescriptor[] {
                new AttributesDescriptor("RESTflow console messages", RfConsoleView.TA_CONSOLE_KEY),
                new AttributesDescriptor("HTTP outgoing", RfConsoleView.TA_HTTP_OUT_KEY),
                new AttributesDescriptor("HTTP incoming (OK)", RfConsoleView.TA_HTTP_IN_OK_KEY),
                new AttributesDescriptor("HTTP incoming (Error)", RfConsoleView.TA_HTTP_IN_ERR_KEY)};
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "RESTflow Console";
    }
}
