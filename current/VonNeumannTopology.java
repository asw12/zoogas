import java.util.*;

public class VonNeumannTopology extends Topology {
    // method to sample a random neighbor of a given cell, returning the directional index
    final public Point getNeighbor(Point p, int ni) {
        Point n = new Point(p);
        int delta = (ni & 2) == 0 ? -1 : +1;
        if ((ni & 1) == 0) {
            n.y += delta;
        }
        else {
            n.x -= delta;
        }
        return n;
    }

    // number of neighbors of any cell (some may be off-board and therefore inaccessible)
    final public int neighborhoodSize() {
        return 4;
    }

    // string representations of cardinal directions
    static private String[] dirStr = { "n", "e", "s", "w" };
    final public String dirString(int dir) {
        return dirStr[dir];
    }

    // method to convert cell coords to graphics coords
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
