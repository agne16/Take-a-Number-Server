package edu.up.projects.engineering;

import org.jasypt.util.text.BasicTextEncryptor;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

/**
 * based on http://cs.lmu.edu/~ray/notes/javanetexamples/
 * A Capitalization Server and Client
 */
public class Server extends WebSocketServer
{
    String rootPath = System.getProperty("user.dir");   //root of project folder
    private String labsFilePath = rootPath + "/LabSessions";

    XMLHelper helper = new XMLHelper();
    private Hashtable<String, LabState> runningStates = new Hashtable<>();

    ArrayList<WebSocket> allConnections = new ArrayList<>();
    ArrayList<WebSocket> tabConnections = new ArrayList<>();

    boolean debug = true;
    boolean verbose = true;

    public Server(InetSocketAddress address)
    {
        super(address);
    }

    //
    //Start of WebSocketServer implementation
    //
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake)
    {
        allConnections.add(conn);
        debugPrint("new connection to " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote)
    {
        allConnections.remove(conn);
        tabConnections.remove(conn);
        debugPrint("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
        debugPrint("Number of tablets: " + tabConnections.size());
        debugPrint("Total connections:: " + allConnections.size());
    }

    @Override
    public void onMessage(WebSocket conn, String message)
    {
        verbosePrint("received message from " + conn.getRemoteSocketAddress() + ": " + message);
        debugPrint("Message Received");
        String header = message.split("#")[0].toLowerCase();
        if(header.equals("encrypted"))
        {
            message = decrypt(message.substring(message.indexOf("#") + 1));
        }
        interpretMessage(conn, message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex)
    {
        System.err.println("an error occured on connection " + conn.getRemoteSocketAddress() + ":" + ex);
    }
    //
    //End of WebSocketServer Implementation
    //

    /**
     * checkpointInit recieves an initial string from a tablet to create all possibles things
     * required for a lab session.
     *
     * Assuming the following input message format:
     * checkpointInit#271,B,02,ComputerScienceLaboratory,3#userId,firstName,lastName,0,0,0...#userId,firstName,lastName,0,0,0
     *
     * successful complete of this method will
     * (1) create a LabState
     * (2) add it to the server's hashtable of currently running LabStates
     * (3) write the labstate xml of the server
     *
     * @param parms an array of String that is the student subsection of the input message
     * @param courseId the course id. Ex: 271 or 371
     * @param courseSection the course section. Ex: A or B
     * @param labNumber the nth number lab of the course. Ex: 01 or 02 or 12
     * @param numCheckpoints number of checkpoints to be completed for the lab
     * @param courseName course name. No spaces, first letter of each word capitalized. Ex: ObjectOrientedDesignLab
     * @return the sessionId String
     */
    public String checkpointInit(String[] parms, int courseId, String courseSection,
                                 String labNumber, int numCheckpoints, String courseName)
    {
        validatePath(labsFilePath);
        //the sessionId is just a composite of elements from the course data
        String sessionId = courseId + courseSection + labNumber;

        //interpret each student's data. add them to the hashtable and roster.
        Hashtable<String, Student> classData = new Hashtable<>();
        ArrayList<String> classRoster = new ArrayList<>();
        for (String s : parms)
        {
            String[] studentData = s.split(",");
            String[] checkpointData = Arrays.copyOfRange(studentData, 3, studentData.length);
            Student student = new Student(studentData[0], studentData[1], studentData[2], checkpointData);
            classData.put(studentData[0], student);
            classRoster.add(studentData[0]);
        }

        //create LabState, add to hashtable, write file
        ArrayList<String> labQueue = new ArrayList<>();
        LabState initLabState = new LabState(sessionId, classData, classRoster, labQueue, numCheckpoints);
        if (runningStates.get(sessionId) == null)
        {
            runningStates.put(sessionId, initLabState);
        }
        helper.writeFile(initLabState, labsFilePath, courseId, courseSection, courseName);

        String checkpointContent = "checkpoint#" + sessionId;
        for (String chk : parms)
        {
            checkpointContent += "#" + chk;
        }
        checkpointWriteTemp(sessionId, checkpointContent);

        return sessionId;
    }

    /**
     * Write checkpoints to a temporary file
     * @param sessionId session id of the lab
     * @param content the one line string to write
     * @return boolean indicating success
     */
    public boolean checkpointWriteTemp(String sessionId, String content)
    {
        if (!content.equals(""))
        {
            //checkpointsTemp = content;
            runningStates.get(sessionId).setCondensedLabString(content);

            verbosePrint("INFO-new checkpoint variable: " + content);
            return true;
        }
        return false;
    }

    /**
     * handles method calls to facilitate syncing of checkpointes
     * @param sessionId session id of the lab
     * @param tabletString full message received from tablet
     * @return boolean indicating success
     */
    public String checkpointSync(String sessionId, String tabletString)
    {
        String localString = runningStates.get(sessionId).getCondensedLabString();
        tabletString = tabletString.replaceFirst("checkpointSync", "checkpoint");
        if (localString.equals(""))//if server has nothing, just take the tablet info
        {
            checkpointWriteTemp(sessionId, tabletString);
            return tabletString;
        }
        String mergedString = checkpointMerge(localString, tabletString);

        //create a new labstate and push it to the file writer
        LabState ls = checkSyncToLabState(mergedString);
        LabState oldLs = runningStates.get(sessionId);
        int courseId = oldLs.getCourseId();
        String courseSection = oldLs.getCourseSection();
        String courseName = oldLs.getCourseName();

        helper.writeFile(ls, labsFilePath, courseId, courseSection, courseName);

        checkpointWriteTemp(sessionId, mergedString);//update the temp file
        return mergedString;
    }


    /**
     * checkpointMerge - a function that merges two Strings (that should represent checkpoint lists) into one
     * format of the string should be CHECKPOINT#SESSION ID#name,cp1,cp2...#name,cp1,cp2... etc
     *
     * @param tabletString - the string from the tablet
     * @param localString  - the string stored on the server
     */
    public String checkpointMerge(String localString, String tabletString)
    {
        //Since server is always updating, consider the tablet to be the most updated
        return tabletString;
    }


    /**
     * validatePath - check if a path exists or create it otherwise
     * @param s the path to be checked
     * @return boolean indicating success
     */
    public boolean validatePath(String s)
    {
        File directory = new File(s);
        return directory.isDirectory() || directory.mkdirs();
    }

    /**
     * Creates a lab state from a network packet string assuming the following format
     * checkpointSync#271B02#userId,firstName,lastName,0,0,0...#userId,firstName,lastName,0,0,0
     * @param packet the condensed string to parse to a lab state
     * @return a lab state
     */
    public LabState checkSyncToLabState(String packet)
    {
        String parms[] = packet.split("#");
        String sessionId = parms[1];
        String[] classStrings = Arrays.copyOfRange(parms, 2, parms.length);
        Hashtable<String, Student> classData = new Hashtable<>();
        ArrayList<String> classRoster = new ArrayList<>();
        for (String s : classStrings)
        {
            String[] studentData = s.split(",");
            String userId = studentData[0];
            String firstName = studentData[1];
            String lastName = studentData[2];
            String[] checkpointData = Arrays.copyOfRange(studentData,3,studentData.length);

            Student student = new Student(userId, firstName, lastName, checkpointData);
            classData.put(userId,student);
            classRoster.add(userId);
        }

        //get number of checkpoints by finding the length of the checkpoint array of the first
        //guy on the class roster grabbed from the hashtable. Messy one-liner. I know.
        int numCheckpoints = classData.get(classRoster.get(0)).getCheckpoints().length;

        ArrayList<String> labQueue = new ArrayList<>();
        return new LabState(sessionId,classData,classRoster,labQueue,numCheckpoints);
    }

    /**
     * Helper method to see if a student is part of a lab sessioin
     *
     * @param studentId the student's id
     * @param sessionId the session id
     * @return true if success, else false
     */
    public boolean authenticateStudent(String sessionId, String studentId)
    {
        if (!runningStates.keySet().contains(sessionId))
        {
            debugPrint("Error in authenticateStudent: session does not exist");
        }
        LabState labState = runningStates.get(sessionId);

        for (String stuID : labState.getClassRoster())
        { // Check each student in roster

            if (stuID.equals(studentId))
                return true;
        }

        return false;
    }

    /**
     * Helper method for a student to enter the queue
     *
     * @param studentId the student's id
     * @param sessionId the session id
     * @return true if success, else false
     */
    public boolean enterQueue(String sessionId, String studentId)
    {
        if (!runningStates.keySet().contains(sessionId.toUpperCase()))
        {
            debugPrint("Error in enterQueue: session does not exist");
        }
        LabState labState = runningStates.get(sessionId);
        ArrayList<String> queue = labState.getLabQueue();

        if (queue.contains(studentId))
        {
            return false;
        }

        queue.add(studentId);
        labState.setLabQueue(queue);
        verbosePrint("Current queue: " + queue);
        return true;
    }

    /**
     * Helper method for a student to leave the queue
     *
     * @param studentId the student's id
     * @param sessionId the session id
     * @return true if success, else false
     */
    public boolean leaveQueue(String sessionId, String studentId)
    {
        if (!runningStates.keySet().contains(sessionId.toUpperCase()))
        {
            debugPrint("Error in leaveQueue: session does not exist");
        }
        LabState labState = runningStates.get(sessionId);
        ArrayList<String> queue = labState.getLabQueue();

        if (queue.contains(studentId))
        {
            queue.remove(studentId);
            labState.setLabQueue(queue);
            verbosePrint("Current queue: " + queue);

            return true;
        }

        return false;
    }

    /**
     * Helper method to interpret an incoming message
     *
     * @param conn the direct connection from whom you recieve the message
     * @param s = the string that was received
     */
    public void interpretMessage(WebSocket conn, String s)
    {
        String input = s;
//        if (input.equals("") || input.equals("."))
//        {
//            //close the connection
//        }

        input = input.trim();
        verbosePrint("Message received: " + input);

        //split the message into so-called "parameters"
        String[] parms = input.split("#");
        switch (parms[0].toLowerCase().trim())//first element of message; processed for interpretation
        {
            case "checkpointinit": //example: checkpointInit#777,A,01,ComputerScienceLab,5#doejo16,John,Doe,0,0,0,0,0#doeja16,John,Doe,0,0,0,0,0#...
                //further split parameters into subparameters
                String[] courseData = parms[1].split(",");
                String[] classData = Arrays.copyOfRange(parms, 2, parms.length);

                //extract
                int courseNumber = Integer.parseInt(courseData[0]);
                String courseSection = courseData[1];
                String labNumber = courseData[2];
                String courseName = courseData[3];
                int numCheckpoints = Integer.parseInt(courseData[4]);

                //create lab sessions, files
                debugPrint("INFO: Invoking 'checkpointInit' command");
                String newSessionId = checkpointInit(classData, courseNumber, courseSection, labNumber, numCheckpoints, courseName);

                conn.send(encrypt("sessionId#" + newSessionId));//format: sessionId#123A01
                break;
            case "checkpointsync": //example: checkpointSync#777A01#doejo16,John,Doe,1,1,1,0,0#doeja16,Jane,Doe,1,1,0,0,0#...
                debugPrint("INFO: checkpointSync method invoked");

                //call checkpointSync with sessionId and the entire input. successes is reported
                String changes = checkpointSync(parms[1], input);
                changes = changes.replaceFirst("checkpoint", "checkpointSync");
                for(WebSocket connection : tabConnections)
                {
                    connection.send(changes); //example: checkpointSync#777A01#doejo16,John,Doe,1,1,1,0,0#doeja16,Jane,Doe,1,1,0,0,0#...
                }
                break;
            case "sessionretrieve": //example: sessionRetrieve#777A01
                String result;
                String sessionIdRetrieve = parms[1];
                if (!runningStates.keySet().contains(parms[1].toUpperCase()))
                {
                    //check if a saved xml file exists to load from
                    File[] files = new File(labsFilePath).listFiles();
                    String filename = "";

                    assert files != null;
                    for (File file : files)
                    {
                        if (file.isFile())
                        {
                            String currFilename = file.getName();
                            if (currFilename.contains(sessionIdRetrieve))
                            {
                                filename = currFilename;
                            }
                        }
                    }

                    LabState ls = helper.parseXML(labsFilePath, filename);
                    if (ls == null)
                    {
                        debugPrint("Error in sessionRetrieve: session does not exist");
                        return;
                    }
                    runningStates.put(sessionIdRetrieve, ls);
                }

                result = runningStates.get(parms[1]).getCondensedLabString();
                if (!result.equals(""))
                {
                    result = result.replaceFirst("checkpoint", "checkpointRetrieve");
                    conn.send(encrypt(result)); //example checkpointRetrieve#777A01#doejo16,John,Doe,1,1,1,0,0#doeja16,Jane,Doe,1,1,0,0,0#...
                }
                else
                {
                    debugPrint("Empty file in sessionRetrieve");
                }
                break;
            case "authenticate": //example: authenticate#777A01#doejo16
                if (authenticateStudent(parms[1], parms[2]))
                {
                    conn.send("User " + parms[2] + " found.");
                }
                else
                {
                    conn.send("User " + parms[2] + " not found.");
                }
                break;
            case "enterqueue": //example: enterQueue#777A01#doejo16
                if (authenticateStudent(parms[1], parms[2]) && enterQueue(parms[1], parms[2]))
                {
                    conn.send("User " + parms[2] + " has been added to the queue.");
                }
                else
                {
                    conn.send("User " + parms[2] + " already exists in queue.");
                }
                break;
            case "leavequeue": //example: leaveQueue#777A01#doejo16
                if (authenticateStudent(parms[1], parms[2]) && leaveQueue(parms[1], parms[2]))
                {
                    conn.send("User " + parms[2] + " has been removed from the queue.");
                    System.out.print("sent1");
                }
                else
                {
                    conn.send("User " + parms[2] + " was not in the queue.");
                    System.out.print("sent2");
                }
                break;
            case "getqueue": //example: getQueue#777A01
                String sessionId = parms[1];
                if (!runningStates.keySet().contains(sessionId.toUpperCase()))
                {
                    debugPrint("Error in leaveQueue: session does not exist");
                }
                LabState labState = runningStates.get(sessionId);
                String queue = labState.getLabQueue().toString();
                conn.send(queue);
                break;
            case "positioninit": //example: positionInit#777A01#4,4,4,3
                String[] layoutParams = parms[2].split(",");
                int leftRow = Integer.parseInt(layoutParams[0]);
                int leftCol = Integer.parseInt(layoutParams[1]);
                int rightRow = Integer.parseInt(layoutParams[2]);
                int rightCol = Integer.parseInt(layoutParams[3]);
                positionInit(parms[1], leftRow, leftCol, rightRow, rightCol);
                break;
            case "identify": //example: identify#tablet or identify#webpage?
                if (parms[1].equals("tablet"))
                {
                    tabConnections.add(conn);
                }
                debugPrint("Number of tablets: " + tabConnections.size());
                debugPrint("Total connections:: " + allConnections.size());
                break;
            case "setposition": //example: setPosition#777A01#agne16#c1r1
                sessionId = parms[1];
                String studentId = parms[2];
                String seat = parms[3];
                if (!runningStates.keySet().contains(sessionId.toUpperCase()))
                {
                    debugPrint("Error in setPosition: session does not exist");
                }
                labState = runningStates.get(sessionId);
                Hashtable<String, String> positions = labState.getSeatPositions();
                if (positions.size() == 0)
                {
                    conn.send("Seats have not been initialized yet!");
                }
                Hashtable<String, Student> students = labState.getClassData();
                Student student = students.get(studentId);
                if (positions.get(seat).equals("unset"))
                {
                    student.setPosition(parms[3]);
                    positions.put(seat,studentId);
                    conn.send("User " + studentId + " now assigned to seat " + seat);
                }
                else
                {
                    conn.send("Seat " + seat + " currently occupied by " + positions.get(seat));
                }
                break;
            case "getpositions": //example: getPositions#777A01 ???
                LabState currState = runningStates.get(parms[1]);
                students = currState.getClassData();
                String message = "positions";
                for (Student student2 : students.values())
                {
                    if (!student2.getPosition().equals("unset"))
                    {
                        int studentQueuePos = currState.getLabQueue().indexOf(student2.getUserId()) + 1;
                        message += "#" + student2.getUserId() +
                                "," + student2.getFirstName() +
                                "," + student2.getLastName() +
                                "," + student2.getPosition() +
                                "," + studentQueuePos;

                    }
                }
                conn.send(message);// example: //positions#doejo16,john,doe,c1r1#doeja16,jane,doe,c4r3...
                break;
            case "getlayout": // example: //getLayout#777A01
                LabState currState2 = runningStates.get(parms[1]);
                int[] layout = currState2.getLabLayout();
                String message2 = "labSize"
                        + "," + layout[0]
                        + "," + layout[1]
                        + "," + layout[2]
                        + "," + layout[3];
                conn.send(message2);// example: //labSize#5,5,4,3
                break;
            case "ireallyreallywanttoclosetheserver":
                debugPrint("Shutting down the server");
                conn.send("Shutting down the server");
                System.exit(0);
                break;
            default:
                //debugPrint("nothing doing");
                conn.send("nothing doing");
                break;
        }
    }

    /**
     * Encrypts a string using Jasypt
     *
     * @param s the string to be encrypted
     * @return the encrypted string with a "encrypted#" header appended to the beginning
     */
    public String encrypt(String s)
    {
        BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
        textEncryptor.setPassword("ForTheLulz");
        String myEncryptedText = textEncryptor.encrypt(s);
        return "encrypted#" + myEncryptedText;
    }

    /**
     * Decrypts a string using Jasypt
     *
     * @param s the string to be decrypted ("encrypted#" header must be stripped off first)
     * @return the decrypted string in plain text
     */
    public String decrypt(String s)
    {
        BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
        textEncryptor.setPassword("ForTheLulz");
        return textEncryptor.decrypt(s);

    }

    public void positionInit(String sessionId, int totalLeftRows, int totalLeftColumns, int totalRightRows, int totalRightColumns)
    {
        if (!runningStates.keySet().contains(sessionId.toUpperCase()))
        {
            debugPrint("Error in positionInit: session does not exist");
        }
        LabState labState = runningStates.get(sessionId);
        labState.setLabLayout(new int[]{totalLeftColumns, totalLeftRows, totalRightColumns, totalRightColumns});
        Hashtable<String, String> positions = labState.getSeatPositions();
        for (int currentRow = 0; currentRow < totalLeftRows; currentRow++)
        {
            for (int currentColumn = 0; currentColumn < totalLeftColumns; currentColumn++)
            {
                int colId = currentColumn + 1;
                int rowId = currentRow + 1;

                String id = "c" + currentColumn + "r" + currentRow;
                if (positions.get(id) == null)
                {
                    positions.put(id, "unset");
                }
//                else
//                {
//                    //not sure if need to overwrite
//                    //positions.put(id, "unset");
//                }
            }
        }

        for (int currentRow = 0; currentRow < totalRightRows; currentRow++)
        {
            for (int currentColumn = 0; currentColumn < totalRightColumns; currentColumn++)
            {
                int colIdOffset = currentColumn + totalLeftColumns;
                int rowId = currentRow;

                String id = "c" + colIdOffset + "r" + currentRow;
                if (positions.get(id) == null)
                {
                    positions.put(id, "unset");
                }
//                else
//                {
//                    //not sure if need to overwrite
//                    //positions.put(id, "unset");
//                }
            }
        }
        labState.setSeatPositions(positions);
    }

    public void debugPrint(String s)
    {
        if (debug)
        {
            System.out.println(s);
        }
    }

    public void verbosePrint(String s)
    {
        if (verbose)
        {
            System.out.println(s);
        }
    }
}
