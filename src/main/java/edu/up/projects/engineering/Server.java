package edu.up.projects.engineering;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * based on http://cs.lmu.edu/~ray/notes/javanetexamples/
 * A Capitalization Server and Client
 */
public class Server
{
    private String rosterCsvFilePath;
    private String savedLabSessionFilePath;
    private LabState currentLabState;
    private boolean running;
    String rootPath = System.getProperty("user.dir");   //root of project folder


    /**
     * Called from ServerMain.main() as a new server instance
     *
     * @throws IOException
     */
    public void runServer() throws IOException
    {
        String serverAddress = "";
        int port = 8080;
        
        //determine ip address of machine
        InetAddress ip;
        try
        {
            ip = InetAddress.getLocalHost();
            serverAddress = ip.toString().split("/")[1];
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
        }

        XMLHelper helper = new XMLHelper();
        String filename = "CS371-C-ComputerScienceLaboratory-64378.xml"; // filename
        currentLabState = helper.parseXML(rootPath, filename);
        //retrieveCheckpoints("17378","127.0.0.1");

        //create an always listening server
        int clientNumber = 0;   // increments every time a new client connects
        running = true;
        ServerSocket listener = new ServerSocket();
        listener.bind(new InetSocketAddress(serverAddress, port));
        System.out.println("Program Server started and reachable at " + serverAddress + ":" + port);
        try
        {
            while (running)
            {
                //create a new instance of a class that reads a network input
                new Writer(listener.accept(), clientNumber++).start();
            }
        }
        finally
        {
            listener.close();
        }
    }

    private class Writer extends Thread
    {
        private Socket socket;
        private int clientNumber;

        public Writer(Socket socket, int clientNumber)
        {
            this.socket = socket;
            this.clientNumber = clientNumber;
            log("New connection with client# " + clientNumber + " at " + socket);
        }

        public void run()
        {
            try
            {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                //out.println("Hello, you are client #" + clientNumber + ".");
                //out.println("Enter a line with only a period to quit\n");

                while (true)
                {
                    //read an interpret any incoming messages
                    String input = in.readLine();
                    input = input.trim().toLowerCase();
                    System.out.println("Message received: " + input);

                    //do note that ServerClient.java will always expect a reply back

                    String[] parms = input.split("#");
                    boolean closed = false;
                    switch (parms[0]) {
                        case "":
                        case ".":
                            System.out.println("Empty String: received");
                            closed = true;  //closes connection with a client. server is still running
                            break;
                        case "append":
                            System.out.println("INFO: Invoking 'append' command");
                            out.println("done " + input);
                            break;
                        case "do something":
                            System.out.println("INFO: Invoking 'doing something' command");
                            doSomething();
                            out.println("done " + input);
                            break;
                        case "checkpoint":
                            System.out.println("INFO: Checkpoint method invoked");

                            String points = retrieveCheckpoints(input.split("#")[1],input.split("#")[2]);
                            out.println(points);//out to tablet
                            break;
                        case "checkpointreply":
                            System.out.println("INFO: Write File invoked");
                            break;
                        default:
                            out.println("nothing doing");
                            out.println("done " + input);
                            break;
                    }
                    if (closed){
                        break;
                    }

                    /*
                    if (input.equals("") || input.equals("."))
                    {
                        System.out.println("Empty String: received");
                        break;  //closes connection with a client. server is still running
                    }
                    else if(input.equals("append"))
                    {
                        System.out.println("INFO: Invoking 'append' command");
                        out.println("done");
                    }
                    else if(input.equals("do something")) {
                        System.out.println("INFO: Invoking 'doing something' command");
                        doSomething();
                        out.println("done");
                    }
                    else if (input.split("#")[0].equals("checkpoint")) //Expecting input such as "checkpoint#<sessionId>#<tablet ip address>"
                    {
                        System.out.println("INFO: Checkpoint method invoked");
                        String points = retrieveCheckpoints(input.split("#")[1],input.split("#")[2]);
                        out.println(points);//out to tablet
                    }
                    else if (input.split("#")[0].equals("checkpointreply")){
                        System.out.println("INFO: Write File invoked");
                    }
                    else //any other message
                    {
                        out.println("nothing doing");
                    }
                    //out.println(input.toUpperCase());
                    */
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            finally
            {
                try
                {
                    socket.close();
                }
                catch (IOException e)
                {
                    log("Couldn't close a socket, what's going on?");
                }
                log("Connection with client# " + clientNumber + " closed");
            }
        }

        private void log(String message) {
            System.out.println(message);
        }
    }


    /**
     *
     * Create a LabState object given a LabSession object
     *
     * @param labSession - the LabSession object to convert
     * @return the LabState
     */
    public LabState initLabState(LabSession labSession)
    {
        String sessionId = labSession.getSessionId();
        int numCheckpoints = labSession.getNumCheckpoints();
        String[] classRoster = labSession.getClassRoster();
        boolean[][] checkpoints = labSession.getCheckpoints();
        ArrayList<String> labQueue = labSession.getLabQueue();

        return new LabState(sessionId, classRoster, checkpoints, labQueue);
    }

    public boolean authenticateStudent(String classRosterPath, String studentID, String filename) throws IOException {

        XMLHelper helper = new XMLHelper();
        LabState labState = helper.parseXML(classRosterPath, filename); // parse the given XML

        for (String stuID : labState.getClassRoster()) { // Check each student in roster

            if (stuID.equals(studentID))
                return true;
        }

        return false;
    }

    public void doSomething()
    {
        String rootPath = System.getProperty("user.dir");   //root of project folder
        String filename = "CS371-C-ComputerScienceLaboratory-64378.xml"; // filename

        // write a sample xml file
        XMLHelper helper = new XMLHelper();

        // parse a sample xml file to an object and print values
        System.out.println(rootPath);
        LabState labState = helper.parseXML(rootPath, filename);
        helper.writeFile(labState);
        System.out.println("Lab Session ID: " + labState.getSessionId());
        System.out.println("Student: " + labState.getClassRoster()[0]);
        System.out.println("Checkpoint 1: " + labState.getCheckpoints()[0][1]);
    }

    /**
     *  Retrieves "network packet" format for checkpoints from file
     *
     * @param sessionId
     * @param ipAddr
     * @return
     */
    public String retrieveCheckpoints(String sessionId, String ipAddr)
    {
        String content = "";
        File file = new File(rootPath + "/" + sessionId +"-checkpoints.txt");
        if(!file.exists())
        {
            initWorkingCheckpoints(sessionId);
            //return "Error: Session does not exist";
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            content = reader.readLine();
            if(content == null){
                System.out.println("Whoa, whoa, whoa. Empty file");
                return "Error";
            }
            return content;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return content;
    }

    /**
     * Converts a lab session checkpoints to "network packet" format
     *
     * @param sessionId
     * @return
     */
    public boolean initWorkingCheckpoints(String sessionId)
    {
        String labSessionId = currentLabState.getSessionId();
        String[] labClassRoster = currentLabState.getClassRoster();
        boolean[][] labCheckpoints = currentLabState.getCheckpoints();

        if (!sessionId.equals(labSessionId))
        {
            System.out.println("Whoa, whoa, whoa. Lab session ID mismatch");
            return false;
        }
        if (labClassRoster.length != labCheckpoints.length)
        {
            System.out.println("Whoa, whoa, whoa. Different lengths for roster and checkpoint list");
            return false;
        }

        File file = new File(rootPath + "/" + sessionId +"-checkpoints.txt");
        try {
            FileWriter write = new FileWriter(file.getAbsoluteFile());
            PrintWriter print_line = new PrintWriter(write);

            String content = "Checkpoint";

            for (int x = 0; x < labClassRoster.length; x++){
                content = content + "#" + labClassRoster[x] + "#";
                for (int y = 0; y < labCheckpoints[x].length; y++){
                    int value = 0;
                    if (labCheckpoints[x][y]) {
                        value = 1;
                    }
                    content = content + value;
                    if (y < labCheckpoints[x].length-1){
                        content = content + ",";
                    }
                }
            }
            //System.out.println(content);
            print_line.write(content);
            print_line.close();
            write.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }


}
