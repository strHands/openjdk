/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.awt.X11;

import java.util.*;
import java.awt.*;
import java.awt.peer.*;
import java.awt.event.*;
import sun.awt.ComponentAccessor;

import sun.awt.*;

class XDialogPeer extends XDecoratedPeer implements DialogPeer {

    private Boolean undecorated;

    XDialogPeer(Dialog target) {
        super(target);
    }

    public void preInit(XCreateWindowParams params) {
        super.preInit(params);

        Dialog target = (Dialog)(this.target);
        undecorated = Boolean.valueOf(target.isUndecorated());
        winAttr.nativeDecor = !target.isUndecorated();
        if (winAttr.nativeDecor) {
            winAttr.decorations = winAttr.AWT_DECOR_ALL;
        } else {
            winAttr.decorations = winAttr.AWT_DECOR_NONE;
        }
        winAttr.functions = MWM_FUNC_ALL;
        winAttr.isResizable =  true; //target.isResizable();
        winAttr.initialResizability =  target.isResizable();
        winAttr.title = target.getTitle();
        winAttr.initialState = XWindowAttributesData.NORMAL;
    }

    public void setVisible(boolean vis) {
        XToolkit.awtLock();
        try {
            Dialog target = (Dialog)this.target;
            if (vis) {
                if (target.getModalityType() != Dialog.ModalityType.MODELESS) {
                    if (!isModalBlocked()) {
                        XBaseWindow.ungrabInput();
                    }
                }
            } else {
                restoreTransientFor(this);
                prevTransientFor = null;
                nextTransientFor = null;
            }
        } finally {
            XToolkit.awtUnlock();
        }

        super.setVisible(vis);
    }

    protected Insets guessInsets() {
        if (isTargetUndecorated()) {
            return new Insets(0, 0, 0, 0);
        } else {
            return super.guessInsets();
        }
    }

    @Override
    boolean isTargetUndecorated() {
        if (undecorated != null) {
            return undecorated.booleanValue();
        } else {
            return ((Dialog)target).isUndecorated();
        }
    }

    int getDecorations() {
        int d = super.getDecorations();
        // remove minimize and maximize buttons for dialogs
        if ((d & MWM_DECOR_ALL) != 0) {
            d |= (MWM_DECOR_MINIMIZE | MWM_DECOR_MAXIMIZE);
        } else {
            d &= ~(MWM_DECOR_MINIMIZE | MWM_DECOR_MAXIMIZE);
        }
        return d;
    }

    int getFunctions() {
        int f = super.getFunctions();
        // remove minimize and maximize functions for dialogs
        if ((f & MWM_FUNC_ALL) != 0) {
            f |= (MWM_FUNC_MINIMIZE | MWM_FUNC_MAXIMIZE);
        } else {
            f &= ~(MWM_FUNC_MINIMIZE | MWM_FUNC_MAXIMIZE);
        }
        return f;
    }

    public void blockWindows(java.util.List<Window> toBlock) {
        Vector<XWindowPeer> javaToplevels = null;
        XToolkit.awtLock();
        try {
            javaToplevels = XWindowPeer.collectJavaToplevels();
            for (Window w : toBlock) {
                XWindowPeer wp = (XWindowPeer)ComponentAccessor.getPeer(w);
                if (wp != null) {
                    wp.setModalBlocked((Dialog)target, true, javaToplevels);
                }
            }
        } finally {
            XToolkit.awtUnlock();
        }
    }

    /*
     * WARNING: don't call client code in this method!
     *
     * The check is performed before the dialog is shown.
     * The focused window can't be blocked at the time it's focused.
     * Thus we don't have to perform any transitive (a blocker of a blocker) checks.
     */
    boolean isFocusedWindowModalBlocker() {
        Window focusedWindow = XKeyboardFocusManagerPeer.getCurrentNativeFocusedWindow();
        XWindowPeer focusedWindowPeer = null;

        if (focusedWindow != null) {
            focusedWindowPeer = (XWindowPeer)ComponentAccessor.getPeer(focusedWindow);
        } else {
            /*
             * For the case when a potential blocked window is not yet focused
             * on the Java level (e.g. it's just been mapped) we're asking for the
             * focused window on the native level.
             */
            focusedWindowPeer = getNativeFocusedWindowPeer();
        }
        synchronized (getStateLock()) {
            if (focusedWindowPeer != null && focusedWindowPeer.modalBlocker == target) {
                return true;
            }
        }
        return super.isFocusedWindowModalBlocker();
    }
}
