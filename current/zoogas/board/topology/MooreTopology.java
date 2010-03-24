package zoogas.board.topology;

import zoogas.board.Point;

public class MooreTopology extends Topology {
    // method to sample a random neighbor of a given cell, returning the directional index
    final public Point getNeighbor(Point p, int ni) {
        Point n = new Point(p);
        if (ni < 3)
            --n.y;
        else if (ni >= 4 && ni <= 6)
            ++n.y;
        if (ni == 0 || ni >= 6)
            --n.x;
        else if (ni >= 2 && ni <= 4)
            ++n.x;
        return n;
    }

    // number of neighbors of any cell (some may be off-board and therefore inaccessible)
    final public int neighborhoodSize() {
        return 8;
    }

    // string representations of cardinal directions
    static private String[] dirStr = { "nw", "n", "ne", "e", "se", "s", "sw", "w" };
    final public String dirString(int dir) {
        return dirStr[dir];
    }

    /**
     * Converts cell coords to graphics coords
     * @Deprecated This method has been moved to BoardRenderer
     * @param pCell
     * @param pixelsPerCell
     * @return
     */
    final public Point getGraphicsCoords(Point pCell, int pixelsPerCell) {
        return new Point(pCell.x * pixelsPerCell, pCell.y * pixelsPerCell);
    }

    /**
     * Converts graphics coords to cell coords
     * @Deprecated This method has been moved to BoardRenderer
     * @param pCell
     * @param pixelsPerCell
     * @return
     */
    final public void getCellCoords(java.awt.Point pGraphics, Point pCell, int pixelsPerCell) {
        pCell.x = pGraphics.x / pixelsPerCell;
        pCell.y = pGraphics.y / pixelsPerCell;
    }
}
