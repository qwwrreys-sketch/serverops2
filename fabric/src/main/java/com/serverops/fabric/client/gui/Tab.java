package com.serverops.fabric.client.gui;

import java.util.List;

/** One sidebar page. {@link #build()} is called whenever server data or local state changes. */
public abstract class Tab {
    protected final OpsScreen scr;

    protected Tab(OpsScreen scr) {
        this.scr = scr;
    }

    public abstract String title();

    public abstract List<Row> build();

    public void onEnter() {}
}
