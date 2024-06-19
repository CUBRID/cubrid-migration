package com.cubrid.cubridmigration.ui.common;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolTip;

public class TooltipLabel extends Label {
    private ToolTip toolTip = null;
    private Composite parent;

    public TooltipLabel(Composite parent, int style) {
        super(parent, style);
        this.parent = parent;

        this.addListener(
                SWT.MouseEnter,
                (event) -> {
                    if (toolTip != null) {
                        toolTip.setVisible(true);
                    }
                });
        this.addListener(
                SWT.MouseExit,
                (event) -> {
                    if (toolTip != null) {
                        toolTip.setVisible(false);
                    }
                });

        this.addDisposeListener(
                new DisposeListener() {
                    @Override
                    public void widgetDisposed(DisposeEvent e) {
                        if (toolTip != null) {
                            toolTip.dispose();
                        }
                    }
                });
    }

    @Override
    public void setToolTipText(String text) {
        toolTip = new ToolTip(parent.getShell(), SWT.BALLOON | SWT.ICON_INFORMATION);
        toolTip.setAutoHide(false);
        toolTip.setVisible(false);
        toolTip.setMessage(text);
    }

    @Override
    protected void checkSubclass() {}
}
