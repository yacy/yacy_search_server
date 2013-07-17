

import java.io.File;
import java.io.FileInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

public class svnRevNrParser extends org.apache.tools.ant.Task{

    private String fileName=null;
    private String property=null;

    public void setFile(String name) {
        this.fileName = name;
    }

    public String getFile() {
        return this.fileName;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public String getProperty() {
        return this.property;
    }

    public void execute() {
        if (this.property==null || this.property.isEmpty()) {
            log("svn entries file name property was not set properly",Project.MSG_ERR);
            return;
        }

        if (this.fileName != null && this.fileName.length() > 0) {
            File entriesFile = new File(this.fileName);
            if (!entriesFile.exists()) throw new BuildException("SVN entries file '" + this.fileName + "' does not exist.");
            if (!entriesFile.canRead()) throw new BuildException("SVN entries file '" + this.fileName + "' is not readable.");
            
            // read the content of the file into memory
            String dataStr;
            try {
                byte[] data = new byte[(int) entriesFile.length()];
                FileInputStream input = new FileInputStream(entriesFile);
                input.read(data);
                dataStr = new String(data);
            } catch (final Exception e) {
                throw new BuildException("Unable to read the SVN entries file '" + this.fileName + "'"); 
            }
            
            // parse the content
            Pattern pattern;
            if (dataStr.startsWith("<?xml")) {
                pattern = Pattern.compile("<entry[^>]*(?:name=\"\"[^>]*revision=\"(\\d*)\"|revision=\"(\\d*)\"[^>]*name=\"\")[^>]*/>"); 
            } else {                
                pattern = Pattern.compile("\\s\\sdir\\s*(\\d*)\\s*(svn(\\+ssh)?|http(s?))://");
            }

            Matcher matcher = pattern.matcher(dataStr);
            String revNr;
            if (matcher.find()) {
                revNr = matcher.group(1);
                if (revNr == null) revNr = matcher.group(2);

                System.out.println(revNr);
                log("SVN revision number found: " + revNr, Project.MSG_VERBOSE);
            } else {
                log("Unable to determine the SVN revision number", Project.MSG_WARN);
                revNr = "0000";
            }
            
            Project theProject = getProject();
            if (theProject != null) {
                theProject.setProperty(this.property, revNr);
                log("Property '" + this.property + "' set to '" + revNr + "'", Project.MSG_VERBOSE);                
            }           
        } else {
            throw new BuildException("File name attribute is required.");
        }

    }   
    
    public static void main(String[] args) {
        svnRevNrParser parser = new svnRevNrParser();
//        parser.setFile(".svn/entries");
//        parser.setProperty("test");
//        parser.execute();
        
        parser.setFile("/home/theli/.eclipse/yacy/.svn/entries");
        parser.setProperty("test");
        parser.execute();
    }
}
