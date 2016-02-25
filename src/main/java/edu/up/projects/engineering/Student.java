package edu.up.projects.engineering;

/**
 * Created by TJ on 2/24/2016.
 */
public class Student
{
    private String firstName;
    private String lastName;
    private String userId;
    private String[] checkpoints;

    public Student(String initUserId, String initFirstName, String initLastName, String[] initCheckpoints)
    {
        this.userId = initUserId;
        this.firstName = initFirstName;
        this.lastName = initLastName;
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

    public String[] getCheckpoints()
    {
        return checkpoints;
    }

    public void setCheckpoints(String[] checkpoints)
    {
        this.checkpoints = checkpoints;
    }
}
