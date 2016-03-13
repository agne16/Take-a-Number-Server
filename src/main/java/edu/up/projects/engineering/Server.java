package edu.up.projects.engineering;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.File;
import java.io.IOException;
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
    private Hashtable<String, LabState> runningStates = new Hashtable<String,LabState>();

    public Server(InetSocketAddress address)
    {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake)
    {
        System.out.println("new connection to " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote)
    {
        System.out.println("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message)
    {
        System.out.println("received message from " + conn.getRemoteSocketAddress() + ": " + message);
        interpretMessage(conn, message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex)
    {
        System.err.println("an error occured on connection " + conn.getRemoteSocketAddress() + ":" + ex);
    }

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
        //the sessionId is just a composite of elements from the course data
        String sessionId = courseId + courseSection + labNumber;

        //interpret each student's data. add them to the hashtable and roster.
        Hashtable<String, Student> classData = new Hashtable();
        ArrayList<String> classRoster = new ArrayList<String>();
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

            System.out.println("INFO-new checkpoint variable: " + content);
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
    public String checkpointMerge(String tabletString, String localString)
    {
        //Should be checkpointSync#<SessionId>#user,first,last#cp1,cp2...#user,first,last#cp1,cp2... etc
        String[] tabletFields = tabletString.split("#");
        String[] serverFields = localString.split("#");

        //merge them
        //ignoring freshly checked for now
        String mergeResult = "";
        mergeResult = mergeResult + tabletFields[0] + "#" + tabletFields[1];

        //index 0 = type of message, index 1 = session id, everything else = checkpoint info for each student
        for (int i = 2; i < tabletFields.length; i++)
        {
            //should be of format user,first,last,cp1,cp2...
            String currTabletString = tabletFields[i];
            String currServerString = serverFields[i];

            //index 0 = name, everything else = checkpoint info
            String[] parsedTablet = currTabletString.split(",");
            String[] parsedServer = currServerString.split(",");

            //should be the name
            mergeResult = mergeResult + "#" + parsedTablet[0];
            mergeResult = mergeResult + "," + parsedTablet[1];
            mergeResult = mergeResult + "," + parsedTablet[2];

            for (int j = 3; j < parsedTablet.length; j++)
            {
                if (parsedTablet[j].equals("1") || parsedServer[j].equals("1"))
                {
                    mergeResult = mergeResult + ",1";
                }
                else
                {
                    mergeResult = mergeResult + ",0";
                }
            }
        }

        return mergeResult;
    }


    /**
     * validatePath - check if a path exists or create it otherwise
     * @param s the path to be checked
     * @return boolean indicating success
     */
    public boolean validatePath(String s)
    {
        File directory = new File(s);
        if (directory.isDirectory())
        {
            return true;
        }
        return directory.mkdirs();
    }

    /**
     * Creates a lab state from a network packet string assuming the following format
     * checkpointSync#271B02#userId,firstName,lastName,0,0,0...#userId,firstName,lastName,0,0,0
     * @param packet
     * @return
     */
    public LabState checkSyncToLabState(String packet)
    {
        String parms[] = packet.split("#");
        String sessionId = parms[1];
        String[] classStrings = Arrays.copyOfRange(parms, 2, parms.length);
        Hashtable<String, Student> classData = new Hashtable<String, Student>();
        ArrayList<String> classRoster = new ArrayList<String>();
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

        ArrayList<String> labQueue = new ArrayList<String>();
        return new LabState(sessionId,classData,classRoster,labQueue,numCheckpoints);
    }

    public boolean authenticateStudent(String classRosterPath, String studentID, String filename) throws IOException
    {

        XMLHelper helper = new XMLHelper();
        LabState labState = helper.parseXML(classRosterPath, filename); // parse the given XML

        for (String stuID : labState.getClassRoster())
        { // Check each student in roster

            if (stuID.equals(studentID))
                return true;
        }

        return false;
    }

    public void interpretMessage(WebSocket conn, String s)
    {
        String input = s;
        if (input.equals("") || input.equals("."))
        {
            //close the connection
        }

        input = input.trim();
        System.out.println("Message received: " + input);

        //do note that ServerClient.java will always expect a reply back

        //acceptable formats for method invoked messages:
        //checkpointInit#271,B,02,ComputerScienceLaboratory,3#userId,firstName,lastName,0,0,0...#userId,firstName,lastName,0,0,0
        //checkpointSync#271B02#userId,firstName,lastName,0,0,0...#userId,firstName,lastName,0,0,0

        //split the message into so-called "parameters"
        String[] parms = input.split("#");
        switch (parms[0].toLowerCase().trim())//first element of message; processed for interpretation
        {
            case "checkpointinit":
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
                System.out.println("INFO: Invoking 'checkpointInit' command");
                String newSessionId = checkpointInit(classData, courseNumber, courseSection, labNumber, numCheckpoints, courseName);

                conn.send("sessionId#" + newSessionId);//format: sessionId#123A01
                break;
            case "checkpointsync":
                System.out.println("INFO: checkpointSync method invoked");

                //call checkpointSync with sessionId and the entire input. successes is reported
                String changes = checkpointSync(parms[1], input);
                changes = changes.replaceFirst("checkpoint", "checkpointSync");
                conn.send(changes);//format: checkpointSync#...
                break;
            case "sessionretrieve":
                String result = "";
                if (!runningStates.keySet().contains(parms[1].toUpperCase()))
                {
                    System.out.println("Error in sessionRetrieve: session does not exist");
                }
                else
                {
                    result = runningStates.get(parms[1]).getCondensedLabString();
                    if (!result.equals(""))
                    {
                        result = result.replaceFirst("checkpoint", "checkpointRetrieve");
                        conn.send(result);
                    }
                    else
                    {
                        System.out.println("Empty file in sessionRetrieve");
                    }
                }
                break;
            case "ireallyreallywanttoclosetheserver":
                System.out.println("Shutting down the server");
                conn.send("Shutting down the server");
                System.exit(0);
            default:
                //System.out.println("nothing doing");
                conn.send("nothing doing");
                break;
        }
    }

}
