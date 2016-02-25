package edu.up.projects.engineering;

/**
 * Created by TJ on 2/24/2016.
 */
public class Student
{
    private String firstName;
    private String lastName;
    private String userId;
    private boolean[] checkpoints;

    public Student(String initFirstName, String initLastName, String initUserId, boolean[] initCheckpoints)
    {
        this.firstName = initFirstName;
        this.lastName = initLastName;
        this.userId = initUserId;
        this.checkpoints = initCheckpoints;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public String getUserId()
    {
        return userId;
    }

    public boolean[] getCheckpoints()
    {
        return checkpoints;
    }

    public void setCheckpoints(boolean[] checkpoints)
    {
        this.checkpoints = checkpoints;
    }
}
