package edu.up.projects.engineering;

import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * The entrypoint of the program
 */
public class ServerMain
{
    public static void main(String[] args) throws IOException
    {
        String host = "localhost";
        int port = 8080;

        //determine ip address of machine
        InetAddress ip;
        try
        {
            ip = InetAddress.getLocalHost();
            host = ip.toString().split("/")[1];
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
        }
        WebSocketServer server = new Server(new InetSocketAddress(host, port));
        System.out.println("Starting server on " + host + ":" + port);
        server.run();
    }

}
