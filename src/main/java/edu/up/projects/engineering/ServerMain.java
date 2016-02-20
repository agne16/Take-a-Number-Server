package edu.up.projects.engineering;

import java.io.IOException;

public class ServerMain
{
    public static void main(String[] args) throws IOException
    {
        Server server = new Server();
        server.runServer();
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
}
