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
import java.util.Arrays;
import java.util.Hashtable;

/**
 * based on http://cs.lmu.edu/~ray/notes/javanetexamples/
 * A Capitalization Server and Client
 */
public class Server
{
    String rootPath = System.getProperty("user.dir");   //root of project folder
    private String labsFilePath = rootPath + "/LabSessions";
    private String labsFilePathTemp = rootPath + "/LabTemp";
    private String rosterCsvFilePath;
    private LabState currentLabState;
    private boolean running;
    XMLHelper helper = new XMLHelper();


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

        validatePath(labsFilePath);
        validatePath(labsFilePathTemp);

        /*
        String filename = "CS371-C-ComputerScienceLaboratory-64378.xml"; // filename
        currentLabState = helper.parseXML(labsFilePath, filename);
        doSomething();
        initWorkingCheckpoints("64378");
        */

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
                    input = input.trim();
                    System.out.println("Message received: " + input);

                    //do note that ServerClient.java will always expect a reply back

                    //acceptable formats for method invoked messages:
                    //checkpointInit#271,B,02,ComputerScienceLaboratory,3#userId,firstName,lastName,0,0,0...#userId,firstName,lastName,0,0,0
                    //checkpointSync#271B02#userId,firstName,lastName,0,0,0...#userId,firstName,lastName,0,0,0

                    String[] parms = input.split("#");
                    boolean closed = false;
                    switch (parms[0].toLowerCase()) {
                        case "":
                        case ".":
                            System.out.println("Empty String: received");
                            closed = true;  //closes connection with a client. server is still running
                            break;
                        case "foo":
                            System.out.println("INFO: Invoking 'foo' command");
                            out.println("done " + input);
                            break;
                        case "checkpointinit":
                            String[] courseData = parms[1].split(",");
                            String[] classData = Arrays.copyOfRange(parms, 2, parms.length);

                            int courseNumber = Integer.parseInt(courseData[0]);
                            String courseSection = courseData[1];
                            String labNumber = courseData[2];
                            String courseName = courseData[3];
                            int numCheckpoints = Integer.parseInt(courseData[4]);

                            System.out.println("INFO: Invoking 'checkpointInit' command");
                            String newSessionId = initCheckpoints(classData, courseNumber, courseSection, labNumber, numCheckpoints, courseName);

                            //doSomething();

                            out.println("Lab Session Created:  " + newSessionId);
                            break;
                        case "checkpointsync":
                            System.out.println("INFO: checkpointSync method invoked");
                            boolean success = syncCheckpoints(parms[1], input);
                            //String points = retrieveCheckpoints(parms[1],parms[2]);
                            out.println(success);//out to tablet
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

        return null;
        //return new LabState(sessionId, classRoster, checkpoints, labQueue);
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
        String filename = "CS371-C-ComputerScienceLaboratory-64378.xml"; // filename

        // write a sample xml file
        XMLHelper helper = new XMLHelper();

        // parse a sample xml file to an object and print values
        System.out.println(labsFilePath);
        LabState labState = helper.parseXML(labsFilePath, filename);
        helper.writeFile(labState, labsFilePath, 777, "A", "The Wardening");

        Student student = labState.getClassData().get(labState.getClassRoster().get(0));
        System.out.println("Lab Session ID: " + labState.getSessionId());
        System.out.println("Student: " + student.getUserId());
        System.out.println("Checkpoint 1: " + student.getCheckpoints()[0]);
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
        File file = new File(labsFilePathTemp + "/" + sessionId +"-checkpoints.txt");
        if(!file.exists())
        {
            initWorkingCheckpoints(sessionId);
            //return "Error: Session does not exist";
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            content = reader.readLine();
            if(content == null){
                System.out.println("WARNING: Whoa, whoa, whoa. Empty empty temp file! We'll create one for you!");
                return null;
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
        if (currentLabState == null)
        {

        }

        String labSessionId = currentLabState.getSessionId();
        Hashtable<String, Student> classData = currentLabState.getClassData();
        ArrayList<String> classRoster = currentLabState.getClassRoster();

        if (!sessionId.equals(labSessionId))
        {
            System.out.println("Whoa, whoa, whoa. Lab session ID mismatch: " + sessionId + " and " + labSessionId);
            return false;
        }

        File file = new File(labsFilePathTemp + "/" + sessionId +"-checkpoints.txt");
        try
        {
            FileWriter write = new FileWriter(file.getAbsoluteFile());
            PrintWriter print_line = new PrintWriter(write);

            String content = "checkpoint";

            for (String s : classRoster) {
                Student student = classData.get(s);
                String[] checkpoints = student.getCheckpoints();
                content = content + "#" + student.getUserId();
                content = content + "#" + checkpoints[0];
                for (int i = 1; i < checkpoints.length; i++){
                   content = content + "," + checkpoints[i];
                }
            }
            //System.out.println(content);
            print_line.write(content);
            print_line.close();
            write.close();
            return true;
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return false;
    }

    public boolean writeCheckpoints(String sessionId, String content) {
        File file = new File(labsFilePathTemp + "/" + sessionId +"-checkpoints.txt");
        try {
            FileWriter write = new FileWriter(file.getAbsoluteFile());
            PrintWriter print_line = new PrintWriter(write);
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

    public boolean syncCheckpoints(String sessionId, String tabletString) {
        String localString = retrieveCheckpoints(sessionId, "");
        if (localString == null) {
            tabletString = tabletString.replaceFirst("checkpointSync", "checkpoint");
            return writeCheckpoints(sessionId, tabletString);
        }
        mergeCheckpoints(localString, tabletString);
        return false;
        //return writeCheckpoints(sessionId, mergedString);
    }

    /**
     * mergeTwo - a function that merges two Strings (that should represent checkpoint lists) into one
     * format of the string should be CHECKPOINT#SESSION ID#name,cp1,cp2...#name,cp1,cp2... etc
     * @param tabletString - the string from the tablet
     * @param localString - the string stored on the server
     */
    public String[] mergeCheckpoints(String tabletString, String localString){
        //Should be checkpointSync#<SessionId>#user,first,last#cp1,cp2...#user,first,last#cp1,cp2... etc
        String[] myFields = tabletString.split("#");
        String[] serverFields = localString.split("#");
        String serversChecks = serverFields[2];


        //merge them
        //ignoring freshly checked for now
        int rosterLength = currentLabState.getClassRoster().size();
        int numChecks = currentLabState.getNumCheckpoints();
        String[] mergeResult = new String[rosterLength];


        //index 0 = type of message, index 1 = session id, everything else = checkpoint info for each student
        for(int i = 2;i<myFields.length;i++){
            //should be of format fullname,cp1,cp2...
            String myCurrent = myFields[i];

            //index 0 = name, everything else = checkpoint info
            String[] parsedMine = myCurrent.split(",");
            String[] parsedServer = serversChecks.split(",");

            //should be the name
            mergeResult[i-2] = parsedMine[0];

            for(int j = 1;j<numChecks+1; j++){
                if(parsedMine[j].equals("1") || parsedServer[j].equals("1")){
                    mergeResult[i-2] = mergeResult[i-2] + ",1";
                }
                else{
                    mergeResult[i-2] = mergeResult[i-2] + ",0";
                }
            }
            mergeResult[i-2] = mergeResult[i-2] + "#";
        }

        return mergeResult;
    }

    public String initCheckpoints(String[] parms, int courseId,String courseSection,
                                  String labNumber, int numCheckpoints, String courseName) {
        //777
        //A
        //01
        // => 777A01

        //parms input: array of the following
        //userId,firstName,lastName,0,0,0
        //userId,firstName,lastName,0,0,0
        //userId,firstName,lastName,0,0,0

        String sessionId = courseId + courseSection + labNumber;
        Hashtable<String, Student> classData= new Hashtable();
        ArrayList<String> classRoster = new ArrayList<String>();

        for (String s : parms)
        {
            String[] studentData = s.split(",");
            String[] checkpointData = Arrays.copyOfRange(studentData,3,studentData.length);
            Student student = new Student(studentData[0], studentData[1], studentData[2], checkpointData);
            classData.put(studentData[0],student);
            classRoster.add(studentData[0]);
        }

        ArrayList<String> labQueue = new ArrayList<>();
        LabState initLabState = new LabState(sessionId, classData, classRoster, labQueue, numCheckpoints);
        helper.writeFile(initLabState, labsFilePath, courseId, courseSection, courseName);

        return sessionId;
    }

    public boolean validatePath(String s) {
        File directory = new File(s);
        if (directory.isDirectory()){
            return true;
        }
        return directory.mkdirs();
    }

}
