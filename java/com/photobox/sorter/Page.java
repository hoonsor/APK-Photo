package com.photobox.sorter;

import android.view.View;

public abstract class Page {
    protected final MainActivity a;
    protected View root;

    public Page(MainActivity a) { this.a = a; }

    public abstract View build();

    public View view() {
        if (root == null) root = build();
        return root;
    }

    public void onShow() { }
    public void onHide() { }
    public void onStoreChanged() { }
    public boolean onBack() { return false; }
}
