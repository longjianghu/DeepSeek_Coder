package com.sohocn.deep.seek.sidebar;

import java.awt.*;

public class VerticalFlowLayout extends FlowLayout {
    public static final int TOP = 0;

    public VerticalFlowLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
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