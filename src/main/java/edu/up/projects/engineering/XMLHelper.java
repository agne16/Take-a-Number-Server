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
import java.util.Hashtable;
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
            int numCheckpoints = Integer.parseInt(students.get(0).getValue()); //child(0) is reserved for numCheckpoints

            //initialize values to be passed onto LabState constructor
            String sessionId = labState.getAttribute("sessionId").getValue();
            ArrayList<String> labQueue = new ArrayList<>();
            Hashtable<String,Student> everyone = new Hashtable<>();

            //For each student
            for(int i = 1; i < students.size(); i++)
            {
                List<Element> currStudentData = students.get(i).getChildren();

                //add the student id to the class roster
                String userId =  students.get(i).getAttribute("userId").getValue();
                String firstName = currStudentData.get(0).getValue().toLowerCase();
                String lastName = currStudentData.get(1).getValue().toLowerCase();
                String[] checkpoints = currStudentData.get(1).getValue().split(",");

                Student student = new Student(userId, firstName, lastName, checkpoints);
                everyone.put(userId, student);
            }

            parsedState = new LabState(sessionId, everyone, labQueue);
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
                    print_line.print("\t\t<checkpoint" + (y+1) + ">" + labState.getCheckpoints()[x][y] + "</checkpoint" + (y+1) + ">\n");
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
}
