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
     * @param rootPath the root path
     * @param filename the full filename of the file (including extension)
     * @return a labstate of the xml
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
            ArrayList<String> classList = new ArrayList<>();

            //For each student
            for(int i = 1; i < students.size(); i++)
            {
                List<Element> currStudentData = students.get(i).getChildren();

                //add the student id to the class roster
                String userId =  students.get(i).getAttribute("userId").getValue();
                String firstName = currStudentData.get(0).getValue().toLowerCase();
                String lastName = currStudentData.get(1).getValue().toLowerCase();
                String[] checkpoints = currStudentData.get(2).getValue().split(",");
                if (checkpoints.length != numCheckpoints) {
                    System.out.println("WARNING: Inconsistent checkpoint list length in parseXML for student " + userId);
                }

                classList.add(userId);

                Student student = new Student(userId, firstName, lastName, checkpoints);
                everyone.put(userId, student);
            }

            parsedState = new LabState(sessionId, everyone, classList, labQueue);
        }
        catch (JDOMException | IOException e)
        {
            e.printStackTrace();
        }

        return parsedState;
    }

    public void writeFile(LabState labState, String dest)
    {
        int courseID = 777;
        String courseSection = "A";
        String courseName = "The Wardening";

        try
        {
            File file = new File(dest + "/CS" + courseSection + courseID + "-" + courseName + "-" + labState.getSessionId() + ".xml");
            if(!file.exists())
            {
                if(!file.createNewFile()){
                    System.out.println("ERROR: Could not create file for lab state");
                }
            }
            FileWriter write = new FileWriter(file.getAbsoluteFile());
            PrintWriter print_line = new PrintWriter(write);
            print_line.print("<?xml version=\"1.0\"?>\n");
            print_line.print("<lab sessionId=\"" + labState.getSessionId() + "\">\n");
            for (String s : labState.getClassRoster()) {
                Student student = labState.getClassData().get(s);
                String[] checkpoints = student.getCheckpoints();
                print_line.print("\t<student userId=\"" + student.getUserId() + "\">\n");
                print_line.print("\t\t<firstname>" + student.getFirstName() + "</firstname>\n");
                print_line.print("\t\t<lastname>" + student.getLastName() + "</lastname>\n");
                print_line.print("\t\t<checkpoints>" + checkpoints[0]);
                for (int i = 1; i < checkpoints.length; i++){
                    print_line.print("," + checkpoints[i]);
                }
                print_line.print("</checkpoints>\n");
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
