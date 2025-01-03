package com.sohocn.deep.seek.window;

import java.awt.*;

public class VerticalFlowLayout extends FlowLayout {
    public static final int TOP = 0;
    public static final int CENTER = 1;
    public static final int BOTTOM = 2;

    private final boolean verticalFill;
    private final int horizontalFill;

    public VerticalFlowLayout(int align, int hgap, int vgap, boolean hfill, boolean vfill) {
        super(align, hgap, vgap);
        this.horizontalFill = hfill ? 1 : 0;
        this.verticalFill = vfill;
    }

    public VerticalFlowLayout() {
        this(TOP, 5, 5, true, false);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        return layoutSize(target, false);
    }

    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxwidth = target.getWidth() - (insets.left + insets.right);
            int maxheight = target.getHeight() - (insets.top + insets.bottom);
            int nmembers = target.getComponentCount();
            int y = insets.top;
            
            for (int i = 0; i < nmembers; i++) {
                Component component = target.getComponent(i);
                if (component.isVisible()) {
                    Dimension d = component.getPreferredSize();
                    component.setBounds(insets.left, y, maxwidth, d.height);
                    y += d.height + getVgap();
                }
            }
        }
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Dimension dim = new Dimension(0, 0);
            int nmembers = target.getComponentCount();
            boolean firstVisibleComponent = true;

            for (int i = 0; i < nmembers; i++) {
                Component component = target.getComponent(i);
                if (component.isVisible()) {
                    Dimension d = preferred ? component.getPreferredSize() : component.getMinimumSize();
                    dim.width = Math.max(dim.width, d.width);
                    if (firstVisibleComponent) {
                        firstVisibleComponent = false;
                    } else {
                        dim.height += getVgap();
                    }
                    dim.height += d.height;
                }
            }

            Insets insets = target.getInsets();
            dim.width += insets.left + insets.right;
            dim.height += insets.top + insets.bottom;
            return dim;
        }
    }
} 