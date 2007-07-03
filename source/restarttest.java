
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class restarttest {
	
	private static final int DEFAULT_BUFFER_SIZE = 4096;
	public static final byte cr = 13;
    public static final byte lf = 10;
    public static final String lfstring = new String(new byte[]{lf});
    
    public static void write(byte[] source, File dest) throws IOException {
        copy(new ByteArrayInputStream(source), dest);
    }
    public static void copy(InputStream source, File dest) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(dest);
            copy(source, fos, -1);
        } finally {
            if (fos != null) try {fos.close();} catch (Exception e) {}
        }
    }
    public static long copy(InputStream source, OutputStream dest, long count) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];                
        int chunkSize = (int) ((count > 0) ? Math.min(count, DEFAULT_BUFFER_SIZE) : DEFAULT_BUFFER_SIZE);
        
        int c; long total = 0;
        while ((c = source.read(buffer,0,chunkSize)) > 0) {
            dest.write(buffer, 0, c);
            dest.flush();
            total += c;
            
            if (count > 0) {
                chunkSize = (int)Math.min(count-total,DEFAULT_BUFFER_SIZE);
                if (chunkSize == 0) break;
            }
            
        }
        dest.flush();
        
        return total;
    }
    
    
    public static void deployScript(File scriptFile, String theScript) throws IOException {
        write(theScript.getBytes(), scriptFile);
        try {
            Runtime.getRuntime().exec("chmod 755 " + scriptFile.getAbsolutePath()).waitFor();
        } catch (InterruptedException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public static void execAsynchronous(File scriptFile) throws IOException {
        // runs a unix/linux script as separate thread
        File starterFile = new File(scriptFile.getAbsolutePath() + ".starter.sh");
        //deployScript(starterFile, "touch restart.starter.startet1");
        deployScript(starterFile, scriptFile.getAbsolutePath() + " &" + lfstring);
        try {
            Runtime.getRuntime().exec(starterFile.getAbsolutePath()).waitFor();
        } catch (InterruptedException e) {
            throw new IOException(e.getMessage());
        }
        starterFile.delete();
    }
    
    public static void restart(File root) {

		try {
			System.out.println("initiated");
			String script = "cd " + root.getAbsolutePath() + lfstring + "while [ -e restarttest.running ]; do" + lfstring + "sleep 1" + lfstring + "done" + lfstring + "java restarttest again";
			File scriptFile = new File(root, "restart.sh");
			deployScript(scriptFile, script);
			System.out.println("wrote restart-script to " + scriptFile.getAbsolutePath());
			execAsynchronous(scriptFile);
			System.out.println("script is running");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    
    public static void main(String[] args) {
    	if (args.length > 0) {
    		File f = new File("restarttest.restartet");
            if (f.exists()) f.delete();
            try {
				f.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	} else {
    		File applicationRoot = new File(System.getProperty("user.dir").replace('\\', '/'));
    		File f = new File("restarttest.running");
            if (f.exists()) f.delete();
            try {
				f.createNewFile();
				f.deleteOnExit();
	    		System.out.println("start-up");
	    		restart(applicationRoot);
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    }

}
