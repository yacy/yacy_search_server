package net.yacy.visualization;

import java.util.ArrayList;
import java.util.List;

public class GridTree {

    private List<GridTree> children;
    
    public GridTree() {
        this.children = null;
    }
    
    public void addChild(GridTree child) {
        if (this.children == null) this.children = new ArrayList<GridTree>();
        this.children.add(child);
    }
    
    public boolean isLeaf() {
        return this.children == null;
    }
    
    public int depth() {
        if (this.isLeaf()) return 1;
        int maxChildDepth = 0;
        for (GridTree child: children) {
            maxChildDepth = Math.max(maxChildDepth, child.depth());
        }
        return maxChildDepth + 1;
    }
    
    public int width() {
        if (this.isLeaf()) return 1;
        int maxChildDepth = 0;
        for (GridTree child: children) {
            maxChildDepth = Math.max(maxChildDepth, child.depth());
        }
        return maxChildDepth + 1;
    }
    
}
