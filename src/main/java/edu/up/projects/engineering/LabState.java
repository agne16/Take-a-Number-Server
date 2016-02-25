package edu.up.projects.engineering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

public class LabState
{
    private String sessionId;
    private ArrayList<String> classRoster;
    private ArrayList<String> labQueue;
    private HashMap<String, Integer> seatPositions;
    private Hashtable<String, Student> classData;
    private int numCheckpoints;

    public LabState(String initSessionId, Hashtable<String,Student> initRoster, ArrayList<String> initClassRoster, ArrayList<String> initLabQueue, int initNumCheckpoints)
    {
        this.sessionId = initSessionId;
        this.classData = initRoster;
        this.classRoster = initClassRoster;
        this.labQueue = initLabQueue;
        this.numCheckpoints = initNumCheckpoints;
    }

    public Hashtable<String, Student> getClassData()
    {
        return classData;
    }

    public void setClassData(Hashtable<String, Student> classData)
    {
        this.classData = classData;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
    }

    public ArrayList<String> getClassRoster()
    {
        return classRoster;
    }

    public void setClassRoster(ArrayList<String> classRoster)
    {
        this.classRoster = classRoster;
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

    public int getNumCheckpoints()
    {
        return numCheckpoints;
    }

    public void setNumCheckpoints(int numCheckpoints)
    {
        this.numCheckpoints = numCheckpoints;
    }
}
