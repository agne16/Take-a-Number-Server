package edu.up.projects.engineering;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;


public class XMLHelper
{
    /**
     *
     * @param rootPath
     * @param filename
     * @return
     */
    public LabState parseXML(String rootPath, String filename)
    {
        LabState parsedState = null;

        try
        {
            //create a file object and make sure it exists
            File file = new File(rootPath + '/' + filename);
            if(!file.exists())
            {
                return null;
            }

            //initialize parser and get the first tag of the xml file
            SAXBuilder saxBuilder = new SAXBuilder();
            Document document = saxBuilder.build(file);
            Element labState = document.getRootElement();

            //Get the array of students
            List<Element> students = labState.getChildren();
            int numCheckpoints = students.get(0).getChildren().size();

            //initialize values to be passed onto LabState constructor
            String sessionId = labState.getAttribute("sessionId").getValue();
            String[] classRoster = new String[students.size()];
            boolean[][] checkpoints = new boolean[students.size()][numCheckpoints];
            ArrayList<String> labQueue = new ArrayList<>();

            //For each student
            for(int i = 0; i < students.size(); i++)
            {
                //add the student id to the class roster
                classRoster[i] = students.get(i).getAttribute("userId").getValue();

                //run for all of the student's checkpoints
                List<Element> studentCheckpoints = students.get(i).getChildren();
                for(int j = 0; j < studentCheckpoints.size(); j++)
                {
                    //convert the string 'true' or 'false' to a boolean
                    String studentId = students.get(i).getAttribute("userId").getValue();
                    String checkpointString = studentCheckpoints.get(j).getValue().toLowerCase();
                    boolean checkpointValue = false;
                    if(checkpointString.equals("true"))
                    {
                        checkpointValue = true;
                    }

                    //write to the array
                    checkpoints[i][j] = checkpointValue;

                    //create a labState
                    parsedState = new LabState(sessionId, classRoster, checkpoints,labQueue);

                }
            }
        }
        catch (JDOMException e)
        {
            e.printStackTrace();
        }

        catch (IOException e)
        {
            e.printStackTrace();
        }

        return parsedState;
    }

    public void writeFile(LabState labState)
    {
        int courseID = 777;
        String courseSection = "A";
        String courseName = "The Wardening";

        try
        {
            String path = System.getProperty("user.dir");
            File file = new File(path + "/CS" + courseSection + courseID + "-" + courseName + "-" + labState.getSessionId() + ".txt");
            if(!file.exists())
            {
                file.createNewFile();
            }
            FileWriter write = new FileWriter(file.getAbsoluteFile());
            PrintWriter print_line = new PrintWriter(write);
            print_line.print("<?xml version=\"1.0\"?>\n");
            print_line.print("<Lab sessionID=\"" + labState.getSessionId() + "\">\n");
            for (int x = 0; x < labState.getClassRoster().length; x++) 
            {
                print_line.print("\t<student userID=\"" + labState.getClassRoster()[x] + "\">\n");
                for (int y = 0; y < labState.getCheckpoints().length-1; y++)
                {
                    print_line.print("\t\t<checkpoint" + y + ">" + labState.getCheckpoints()[x][y] + "</checkpoint" + y + ">\n");
                }
                print_line.print("\t</student>\n");
            }
            print_line.print("</lab>");
            print_line.close();
            write.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    public void blah()
    {
        System.out.println("success");
    }
}
