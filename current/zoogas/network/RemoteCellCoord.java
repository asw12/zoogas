package zoogas.network;

import zoogas.board.Point;

import java.net.*;

public class RemoteCellCoord {
    InetSocketAddress sockAddr;
    public InetAddress addr = null;
    public int port = -1;
    public Point p = null;

    public RemoteCellCoord(InetSocketAddress sockAddr, Point p) {
        this.sockAddr = sockAddr;
        this.addr = sockAddr.getAddress();
        this.port = sockAddr.getPort();
        this.p = new Point(p);
    }
}
