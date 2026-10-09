/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-6.
 */

package eu.mikus.edziennik.ui.widgets;

import com.google.gson.annotations.SerializedName;

public class WidgetConfig {
    public int profileId = -1;
    public boolean bigStyle = false;
    public boolean darkTheme = false;

    /**
     * Serialised as "alpha", deliberately NOT as "opacity".
     *
     * Every widget placed before opacity was honoured has an "opacity" key in its stored config --
     * usually 0.6 or 0.8, which WidgetConfigActivity set as its initial field value and nothing ever
     * read. Binding to that key would turn every existing widget translucent on its next update,
     * which no user asked for and every user can see. Under the new name those entries no longer
     * bind and this keeps its 1.0f default, so placed widgets look exactly as they do today and the
     * slider takes effect from the next time someone actually moves it.
     *
     * What that discards is a value which has had no visible effect on any device since Android 9,
     * so nobody has an expectation attached to it. The stale "opacity" key stays in the stored JSON
     * until that widget is next reconfigured, which rewrites the whole entry; nothing reads it in
     * the meantime, and purging it eagerly would need a migration for no gain.
     */
    @SerializedName("alpha")
    public float opacity = 1.0f;

    /**
     * Gson needs this. Without a no-arg constructor it instantiates via Unsafe.allocateInstance,
     * which zeroes every field and never runs the initialisers above -- so a config whose JSON is
     * missing a key gets 0, not the declared default. For {@link #opacity} that is the difference
     * between a widget that is fully opaque and one that is fully INVISIBLE, and every config
     * written before this change is missing the "alpha" key by design.
     */
    public WidgetConfig() {
    }

    public WidgetConfig(int profileId) {
        this.profileId = profileId;
    }

    public WidgetConfig(int profileId, boolean bigStyle, boolean darkTheme, float opacity) {
        this.profileId = profileId;
        this.bigStyle = bigStyle;
        this.darkTheme = darkTheme;
        this.opacity = opacity;
    }
}
