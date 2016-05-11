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
 * The primary class of the program
 * Network connections, message interpretation, and data manipulation is handled here
 *
 */
public class Server extends WebSocketServer
{
    String rootPath = System.getProperty("user.dir");   //root of project folder
    private String labsFilePath = rootPath + "/LabSessions";

    //Object that handles XML parsing (see XMLHelper.java)
    XMLHelper helper = new XMLHelper();

    //Currently running states (i.e. hold the lab session of each individual room at a given time)
    private Hashtable<String, LabState> runningStates = new Hashtable<>();

    //Used to keep track of who is still actively connected
    ArrayList<WebSocket> allConnections = new ArrayList<>();
    ArrayList<WebSocket> tabConnections = new ArrayList<>();
    Hashtable<String, WebSocket> webConnections = new Hashtable<>();

    //Switches to determine how much is outputted to the console
    //When both set to false, no extra messages outputted except for system errors
    boolean debug = true; //Network connect-disconnects, queue, method calls, errors
    boolean verbose = false; //Raw input messages, interpreted messages, checkpoint plain text

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
        //Need to do extra bookkeeping if it's a connection from the web page
        if(webConnections.containsValue(conn))
        {
            //Verify that there is a web connection whose bound to some student
            String studentId = "none";
            for (String s : webConnections.keySet())
            {
                if (webConnections.get(s).equals(conn))
                {
                    studentId = s;
                }
            }
            for (LabState ls : runningStates.values())//NOTE: Dangerous! Can get big. Running states are not discarded until server is restarted
            {
                removeTraces(ls, studentId);//remove the student from all running states and it variables
            }
        }
        //Remove connection from all of our array lists
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

        //isolate the header and decrypt it if it needs it
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
        //Generic error catcher
        //Note that the server will continue to run in most cases
        System.err.println("an error occurred on connection " + conn.getRemoteSocketAddress() + ":" + ex);
    }
    //
    //End of WebSocketServer Implementation
    //

    /**
     * checkpointInit receives an initial string from a tablet to create all possibles things
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
        validatePath(labsFilePath);//ensure that the path exists

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
        //if there's data, set it in the state's condensted string variable
        if (!content.equals(""))
        {
            runningStates.get(sessionId).setCondensedLabString(content);

            verbosePrint("INFO-new checkpoint variable: " + content);
            return true;
        }
        return false;//string was empty
    }

    /**
     * handles method calls to facilitate syncing of checkpoints
     * @param sessionId session id of the lab
     * @param tabletString full message received from tablet
     * @return boolean indicating success
     */
    public String checkpointSync(String sessionId, String tabletString)
    {
        //check the string that the state has on hand,
        //merge if there's data, or just take the tablet's data
        String localString = runningStates.get(sessionId).getCondensedLabString();
        tabletString = tabletString.replaceFirst("checkpointSync", "checkpoint");
        if (localString.equals(""))
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
        ls.setLabLayout(oldLs.getLabLayout());

        //write it to its "persistent" file
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
        String parms[] = packet.split("#");//split the string to an array

        //IDX 0 = "checkpointSync"
        //IDX 1 = sessionId
        //IDX 2 = "userId,firstName,lastName,0,0,0"
        //IDX 3 = "userId,firstName,lastName,0,0,0"
        //...
        //IDX n = "userId,firstName,lastName,0,0,0"

        String sessionId = parms[1];
        String[] classStrings = Arrays.copyOfRange(parms, 2, parms.length);//create array of only the student strings

        //needed for LabState constructor
        Hashtable<String, Student> classData = new Hashtable<>();
        ArrayList<String> classRoster = new ArrayList<>();
        ArrayList<String> labQueue = new ArrayList<>();

        //process all of the student strings
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

        return new LabState(sessionId,classData,classRoster,labQueue,numCheckpoints);
    }

    /**
     * Helper method to see if a student is part of a lab session
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
        debugPrint("Current queue: " + queue);
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
            debugPrint("Current queue: " + queue);

            for (String student : queue)
            {
                WebSocket ws = webConnections.get(student);
                int queuePos = queue.indexOf(student) + 1;
                ws.send("Your position in the queue is: " + queuePos);
            }

            return true;
        }

        return false;
    }

    /**
     * Helper method to interpret an incoming message
     *
     * @param conn the direct connection from whom you receive the message
     * @param s = the string that was received
     */
    public void interpretMessage(WebSocket conn, String s)
    {
        String input = s;

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
                debugPrint("INFO: sessionRetrieve method invoked");
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
                    int[] layoutRetrieve = ls.getLabLayout();
                    positionInit(sessionIdRetrieve, layoutRetrieve[0], layoutRetrieve[1], layoutRetrieve[2], layoutRetrieve[3]);
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
                debugPrint("INFO: authenticate method invoked");
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
                debugPrint("INFO: enterQueue method invoked");
                if (authenticateStudent(parms[1], parms[2]) && enterQueue(parms[1], parms[2]))
                {
                    conn.send("User " + parms[2] + " has been added to the queue.");
                    for(WebSocket connection : tabConnections)
                    {
                        String message = generateQueueString(parms[1]);
                        message = message.replaceFirst("positions", "positionSync");
                        connection.send(message); //example: positionsSync#777A01#...
                    }
                }
                else
                {
                    conn.send("User " + parms[2] + " already exists in queue.");
                }
                break;
            case "leavequeue": //example: leaveQueue#777A01#doejo16
                debugPrint("INFO: leaveQueue method invoked");
                if (authenticateStudent(parms[1], parms[2]) && leaveQueue(parms[1], parms[2]))
                {
                    conn.send("User " + parms[2] + " has been removed from the queue.");
                    for(WebSocket connection : tabConnections)
                    {
                        String message = generateQueueString(parms[1]);
                        message = message.replaceFirst("positions", "positionSync");
                        connection.send(message); //example: positionsSync#777A01#...
                    }
                }
                else
                {
                    conn.send("User " + parms[2] + " was not in the queue.");
                }
                break;
            case "getqueue": //example: getQueue#777A01
                debugPrint("INFO: getQueue method invoked");
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
                debugPrint("INFO: positionInit method invoked");
                String[] layoutParams = parms[2].split(",");
                int leftRow = Integer.parseInt(layoutParams[0]);
                int leftCol = Integer.parseInt(layoutParams[1]);
                int rightRow = Integer.parseInt(layoutParams[2]);
                int rightCol = Integer.parseInt(layoutParams[3]);
                positionInit(parms[1], leftRow, leftCol, rightRow, rightCol);
                break;
            case "identify": //example: identify#tablet or identify#webpage?
                debugPrint("INFO: identify method invoked");
                if (parms[1].equals("tablet"))
                {
                    tabConnections.add(conn);
                }
                else if (parms[1].equals("webpage"))
                {
                    webConnections.put(parms[2], conn);
                }
                debugPrint("Number of tablets: " + tabConnections.size());
                debugPrint("Total connections:: " + allConnections.size());
                break;
            case "setposition": //example: setPosition#777A01#agne16#c1r1
                debugPrint("INFO: setPosition method invoked");
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
                    if (!student.getPosition().equals("unset"))
                    {
                        labState.getSeatPositions().put(student.getPosition(),"unset");
                        student.setPosition("unset");
                    }
                    student.setPosition(parms[3]);
                    positions.put(seat,studentId);
                    conn.send("User " + studentId + " now assigned to seat " + seat);
                    for(WebSocket connection : tabConnections)
                    {
                        String message = generateQueueString(parms[1]);
                        message = message.replaceFirst("positions", "positionSync");
                        connection.send(message); //example: positionsSync#777A01#...
                    }
                }
                else
                {
                    conn.send("Seat " + seat + " currently occupied by " + positions.get(seat));
                }
                break;
            case "getpositions": //example: getPositions#777A01 ???
                debugPrint("INFO: getPositions method invoked");
                String message = generateQueueString(parms[1]);
                conn.send(message);// example: //positions#doejo16,john,doe,c1r1#doeja16,jane,doe,c4r3...
                break;
            case "getlayout": // example: //getLayout#777A01
                debugPrint("INFO: getLayout method invoked");
                LabState currState2 = runningStates.get(parms[1]);
                int[] layout = currState2.getLabLayout();
                String message2 = "labSize"
                        + "#" + layout[0]
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
                conn.send("A message was received, no action was taken");
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
        textEncryptor.setPassword("ForTheLulz");//TODO Please do change this privately or the GitHub boogeymen will get you
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
        textEncryptor.setPassword("ForTheLulz");//TODO Please do change this privately or the GitHub boogeymen will get you
        return textEncryptor.decrypt(s);

    }

    /**
     * Sets the size of the room for the lab session
     *
     * @param sessionId the session to set the size of room
     * @param totalLeftRows number of rows on left side of room
     * @param totalLeftColumns number of columns on left side of room
     * @param totalRightRows number of rows on right side of room
     * @param totalRightColumns number of columns on right side of room
     */
    public void positionInit(String sessionId, int totalLeftRows, int totalLeftColumns, int totalRightRows, int totalRightColumns)
    {
        if (!runningStates.keySet().contains(sessionId.toUpperCase()))
        {
            debugPrint("Error in positionInit: session does not exist");
        }
        LabState labState = runningStates.get(sessionId);
        labState.setLabLayout(new int[]{totalLeftRows, totalLeftColumns, totalRightRows, totalRightColumns});//set lab layout array in state
        Hashtable<String, String> positions = labState.getSeatPositions();

        //Initialize left side of room as a hashtable
        //Keys are strings of the form cXrY where X is a column number, Y is a row number
        //Values are the studentId of the seat occupant
        //Otherwise, it's set to unset
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
            }
        }

        //Initialize right side of room as a hashtable
        //Keys are strings of the form cXrY where X is a column number, Y is a row number
        //Columns need an offset made by the left side of the room
        //Values are the studentId of the seat occupant
        //Otherwise, it's set to unset
        for (int currentRow = 0; currentRow < totalRightRows; currentRow++)
        {
            for (int currentColumn = 0; currentColumn < totalRightColumns; currentColumn++)
            {
                int colIdOffset = currentColumn + totalLeftColumns;

                String id = "c" + colIdOffset + "r" + currentRow;
                if (positions.get(id) == null)
                {
                    positions.put(id, "unset");
                }
            }
        }

        //set data, write to file
        labState.setSeatPositions(positions);
        int courseId = labState.getCourseId();
        String courseSection = labState.getCourseSection();
        String courseName = labState.getCourseName();
        helper.writeFile(labState, labsFilePath, courseId, courseSection, courseName);
    }

    /**
     * Helper method to print a message only if the debug boolean is set
     *
     * @param s the message to print
     */
    public void debugPrint(String s)
    {
        if (debug)
        {
            System.out.println(s);
        }
    }

    /**
     * Helper method to print a message only if the verbose boolean is set
     *
     * @param s the message to print
     */
    public void verbosePrint(String s)
    {
        if (verbose)
        {
            System.out.println(s);
        }
    }

    /**
     * Remove a student's queue position on disconnect.
     * Also release the student's occupied seat
     *
     * @param ls the lab state to make changes to
     * @param student the studentId of the student to remove
     */
    public void removeTraces(LabState ls, String student)
    {
        leaveQueue(ls.getSessionId(), student);

        //retrieve student's seats
        Student st = ls.getClassData().get(student);
        String seat = st.getPosition();

        //set seats to available
        ls.getSeatPositions().put(seat, "unset");
        st.setPosition("unset");
    }

    /**
     * Converts the queue array list to a String for the tablets to interpret
     *
     * @param sessionId the session id of the lab
     * @return the string of the current queue
     */
    public String generateQueueString(String sessionId)
    {
        LabState currState = runningStates.get(sessionId);
        Hashtable<String, Student> students = currState.getClassData();
        String message = "positions";//Header for the message

        //check every student in the class
        //if the student has a seat that they occupy,
        //append their information on the string
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
        return message;
    }
}
