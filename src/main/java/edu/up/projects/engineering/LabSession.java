package edu.up.projects.engineering;

import java.util.ArrayList;

public class LabSession
{
    private String sessionId;
    private int numCheckpoints;
    private String[] classRoster;
    private boolean[][] checkpoints;
    private ArrayList<String> labQueue;

    public LabSession(int initNumCheckpoints, String[] initClassRoster)
    {
        numCheckpoints = initNumCheckpoints;
        classRoster = initClassRoster;
        checkpoints = new boolean[classRoster.length][numCheckpoints];
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public int getNumCheckpoints()
    {
        return numCheckpoints;
    }

    public String[] getClassRoster()
    {
        return classRoster;
    }

    public boolean[][] getCheckpoints()
    {
        return checkpoints;
    }

    public ArrayList<String> getLabQueue()
    {
        return labQueue;
    }


}
