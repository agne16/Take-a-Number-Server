package edu.up.projects.engineering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

public class LabState
{
    private String sessionId;
    private String[] classRoster;
    private boolean[][] checkpoints;
    private ArrayList<String> labQueue;
    private HashMap<String, Integer> seatPositions;

    public LabState(String initSessionId, String[] initClassRoster, boolean[][] initCheckpoints,
                    ArrayList<String> initLabQueue)
    {
        sessionId = initSessionId;
        classRoster = initClassRoster;
        checkpoints = initCheckpoints;
        labQueue = initLabQueue;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
    }

    public String[] getClassRoster()
    {
        return classRoster;
    }

    public void setClassRoster(String[] classRoster)
    {
        this.classRoster = classRoster;
    }

    public boolean[][] getCheckpoints()
    {
        return checkpoints;
    }

    public void setCheckpoints(boolean[][] checkpoints)
    {
        this.checkpoints = checkpoints;
    }

    public ArrayList<String> getLabQueue()
    {
        return labQueue;
    }

    public void setLabQueue(ArrayList<String> labQueue)
    {
        this.labQueue = labQueue;
    }

    public HashMap<String, Integer> getSeatPositions()
    {
        return seatPositions;
    }

    public void setSeatPositions(HashMap<String, Integer> seatPositions)
    {
        this.seatPositions = seatPositions;
    }

    ////
    //Test Constructor
    ////

    Hashtable<String, Student> roster;
    public LabState(String initSessionId, Hashtable<String,Student> initRoster, ArrayList<String> initLabQueue)
    {
        this.sessionId = initSessionId;
        this.roster = initRoster;
        this.labQueue = initLabQueue;
    }

    public Hashtable<String, Student> getRoster()
    {
        return roster;
    }

    public void setRoster(Hashtable<String, Student> roster)
    {
        this.roster = roster;
    }
}
