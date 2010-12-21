package uk.me.graphe.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class GraphemeServer extends Thread {

    public static final int GAPHEME_PORT = 6689;
    private static GraphemeServer sInstance = null;

    public static GraphemeServer getInstance() {
        if (sInstance == null) sInstance = new GraphemeServer();
        return sInstance;
    }

    private ClientMessageHandler mClientMessageHandler;

    private boolean mRunning = false;

    private ServerSocketChannel mServerSocketChannel;

    private GraphemeServer() {
        try {
            // sets up a server socket listening on the grapheme port
            mServerSocketChannel = ServerSocketChannel.open();
            mServerSocketChannel.socket().bind(new InetSocketAddress(GAPHEME_PORT));
            // let's make sure for certain we're up and running
            assert mServerSocketChannel.isOpen();

            // start a new client message handler: it's going to accept incoming
            // data from clients
            mClientMessageHandler = new ClientMessageHandler();
            mClientMessageHandler.start();
        } catch (IOException e) {
            throw new Error("couldn't start server", e);
        }

    }

    public boolean isShutDown() {
        return !mRunning;
    }

    @Override
    public void run() {
        while (mRunning) {
            try {
                // accept incoming connections and let the client message
                // handler know about them
                SocketChannel clientSock = mServerSocketChannel.accept();
                mClientMessageHandler.addClient(clientSock);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    public void shutDown() {
        mRunning = false;
    }

    @Override
    public synchronized void start() {
        mRunning = true;
        super.start();
    }

}