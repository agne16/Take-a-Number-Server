package edu.up.projects.engineering;

/**
 * Student object class
 */
public class Student
{
    private String firstName;
    private String lastName;
    private String userId;
    private String[] checkpoints;

    /**
     * Constructor for a Student object
     *
     * @param initUserId the student's userId (ex: doe16)
     * @param initFirstName the student's first name (ex: john)
     * @param initLastName the student's last name (ex: doe)
     * @param initCheckpoints a string array of checkpoints completed (ex: [1, 1, 0, 0])
     */
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
