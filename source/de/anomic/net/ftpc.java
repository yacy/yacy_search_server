// ftpc.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// main implementation finished: 28.05.2002
// last major change: 06.05.2004
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.net;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;

import de.anomic.server.serverCore;

public class ftpc {

    private static final String vDATE = "20040506";
    private static final String logPrefix = "FTPC: ";

  
  private InputStream in;
  private PrintStream out;
  private PrintStream err;
  private boolean glob = true; // glob = false -> filenames are taken literally for mget, ..

  // for time measurement
  private static final TimeZone GMTTimeZone = TimeZone.getTimeZone("PST"); // the GMT Time Zone

  // transfer type
  private static final char transferType = 'i'; // transfer binary
 
  // block size [1K by default]
  private static final int blockSize = 1024;
  
  // client socket for commands
  private Socket ControlSocket = null;

  // socket for data transactions
  private ServerSocket DataSocketActive = null;
  private Socket       DataSocketPassive = null;
  private boolean      DataSocketPassiveMode = true;

  // output and input streams for client control connection
  private BufferedReader   clientInput = null;
  private DataOutputStream clientOutput = null;

  // server this client is connected to
  private String account = null;
  
  // client prompt
  private String prompt = "ftp [local]>";

  String[] cmd;

  File currentPath;
  
  public ftpc() {
      this(System.in, System.out, System.err);
  }
  
  public ftpc(java.io.InputStream ins, java.io.PrintStream outs, java.io.PrintStream errs) {
      
      try {
	  System.setSecurityManager(new sm());
      } catch (java.security.AccessControlException e) {
      }
      
      this.in = ins;
      this.out = outs;
      this.err = errs;
      
      this.currentPath = new File(System.getProperty("user.dir"));
      try {
	  this.currentPath = new File(this.currentPath.getCanonicalPath());
      } catch (IOException e) {}
      
  }

    public void shell(String server) {
	String command;
	
	java.io.PrintWriter pw = null;
	if (out != null) {
	    pw = new java.io.PrintWriter(out);
	}
	
	try {
	    java.io.BufferedReader stdin =
		new java.io.BufferedReader(new java.io.InputStreamReader(in));
	    if (server != null) exec("open " + server, true);
	    while (true) {
		
		// prompt
		if (pw != null) {pw.print(prompt); pw.flush();}
		
		// read a line
		while ((command = stdin.readLine()) == null)
		    if (pw != null) {pw.print(prompt); pw.flush();}
		
		// execute
		if (!exec(command, false)) break;
		
	    }
	} catch (Exception e) {
	    err.println(logPrefix + "---- Error - ftp exception: " + e);
	    e.printStackTrace(err);
	}
    }

  public boolean exec(String command, boolean promptIt) {
    if ((command == null) || (command.length() == 0)) return true;
    int pos;
    String com;
    boolean ret = true;
    while (command.length() > 0) {
      pos = command.indexOf(";"); if (pos < 0) pos = command.indexOf("\n");
      if (pos < 0) {
        com = command;
        command = "";
      } else {
      	com = command.substring(0,pos);
      	command = command.substring(pos + 1);
      }
      if (promptIt) out.println(logPrefix + prompt + com);
      cmd = line2args(com);
      try {
        ret = (((Boolean) getClass().getMethod(
                cmd[0].toUpperCase(),
                new Class[0]
                ).invoke(this, new Object[0])).booleanValue());
      } catch (InvocationTargetException e) {
	if (e.getMessage() == null) {}
	else if (ControlSocket == null) {
	  // the error was probably caused because there is no connection
          err.println(logPrefix + "---- not connected. no effect.");
	  e.printStackTrace();
	  return ret;
	} else {
          err.println(logPrefix + "---- ftp internal exception: target exception " + e);
          return ret;
	}
      } catch (IllegalAccessException e) {
        err.println(logPrefix + "---- ftp internal exception: wrong access " + e);
        return ret;
      } catch (NoSuchMethodException e) {
	// consider first that the user attempted to execute a java command from
	// the current path; either local or remote
	if (ControlSocket == null) {
	  // try a local exec
	  try {
	    javaexec(cmd);
	  } catch (Exception ee) {
            err.println(logPrefix + "---- Command '" + cmd[0] + "' not supported. Try 'HELP'.");
	  }
	} else {
	  // try a remote exec
	  exec("java " + com, false);
	}
        return ret;
      }
    }
    return ret;
  }
  
                          
  private String[] line2args(String line) {
    // parse the command line
    if ((line == null) || (line.length() == 0)) return null;
    // pre-parse
    String line1="";
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      if (quoted) {
        if (line.charAt(i) == '"') {
          quoted = false;
        } else {
          line1 = line1 + line.charAt(i);
        }
      } else {
        if (line.charAt(i) == '"') {
          quoted = true;
        } else if (line.charAt(i) == ' ') {
          line1 = line1 + '|';
        } else {
          line1 = line1 + line.charAt(i);
        }
      }
    }
    // construct StringTokenizer
    String args[];
    StringTokenizer st = new StringTokenizer(line1,"|");
    // read tokens from string
    args = new String[st.countTokens()];
    for (int i = 0; st.hasMoreTokens(); i++) {
        args[i] = st.nextToken();
    }
    st = null;
    return args;
  }
  
  private static String[] shift(String args[]) {
    if ((args == null) || (args.length == 0)) return args; else {
      String[] newArgs = new String[args.length-1];
      System.arraycopy(args, 1, newArgs, 0, args.length-1);
      return newArgs;
    }
  }

  class cl extends ClassLoader {
  		
    public cl() {
      super();
    }
  
    public Class loadClass(String classname, boolean resolve) throws ClassNotFoundException {
        Class c = findLoadedClass(classname);
        if (c == null) try {
        	// second try: ask the system
          c = findSystemClass(classname);
        } catch (ClassNotFoundException e) {
        	// third try: load myself
          File f = new File(System.getProperty("user.dir"), classname + ".class");
          int length = (int)f.length();
          byte[] classbytes = new byte[length];
          try {
            DataInputStream in = new DataInputStream(new FileInputStream(f));
            in.readFully(classbytes);
            in.close();
            c = defineClass(classname, classbytes, 0, classbytes.length);
          } catch (FileNotFoundException ee) {
            throw new ClassNotFoundException();
          } catch (IOException ee) {
            throw new ClassNotFoundException();
          }
        }
        if (resolve) resolveClass(c);
        return c;
    }
  
  }
  
  private void javaexec(String[] inArgs) {
    String obj = inArgs[0];
    String[] args = new String[inArgs.length-1];

    // remove the object name from the array of arguments
    System.arraycopy(inArgs, 1, args, 0, inArgs.length-1);

    // Build the argument list for invoke() method.
    Object[] argList = new Object[1];
    argList[0] = args;

    Properties pr = System.getProperties();
    String origPath = (String) pr.get("java.class.path");
    try {
      
      // set the user.dir to the actual local path
      pr.put("user.dir", this.currentPath.toString());
	
      // add the current path to the classpath
      //pr.put("java.class.path", "" + pr.get("user.dir") + pr.get("path.separator") + origPath);
      
      //err.println(logPrefix + "System Properties: " + pr.toString());
      
      System.setProperties(pr);
      
      // locate object
      Class c = (new cl()).loadClass(obj);
      //Class c = this.getClass().getClassLoader().loadClass(obj);
      
      // locate public static main(String[]) method
      Class[] parameterType = new Class[1];
      parameterType[0] = Class.forName("[Ljava.lang.String;");
      Method m = c.getMethod("main", parameterType);
      
      // invoke object.main()
      Object result = m.invoke(null, argList);
      parameterType = null;
      m = null;

      // handle result
      if (result != null) out.println(logPrefix + "returns " + result);

      // set the local path to the user.dir (which may have changed)
      this.currentPath = new File((String) pr.get("user.dir"));

    } catch (ClassNotFoundException e) {
      // err.println(logPrefix + "---- cannot find class file " + obj + ".class");
      // class file does not exist, go silently over it to not show everybody that the
      // system attempted to load a class file
      err.println(logPrefix + "---- Command '" + obj + "' not supported. Try 'HELP'.");
    } catch (NoSuchMethodException e) {
      err.println(logPrefix + "---- no \"public static main(String args[])\" in " + obj);
    } catch (InvocationTargetException e) {
        Throwable orig = e.getTargetException();
	if (orig.getMessage() == null) {} else {
          err.println(logPrefix + "---- Exception from " + obj + ": " + orig.getMessage());
          orig.printStackTrace(err);
	}
    } catch (IllegalAccessException e) {
      err.println(logPrefix + "---- Illegal access for " + obj + ": class is probably not declared as public");
      e.printStackTrace(err);
    } catch (NullPointerException e) {
      err.println(logPrefix + "---- main(String args[]) is not defined as static for " + obj);
/*
    } catch (IOException e) {
      // class file does not exist, go silently over it to not show everybody that the
      // system attempted to load a class file
      err.println(logPrefix + "---- Command '" + obj + "' not supported. Try 'HELP'.");
*/
    } catch (Exception e) {
      err.println(logPrefix + "---- Exception caught: " + e);
      e.printStackTrace(err);
    }

    // set the classpath to its original definition
    pr.put("java.class.path", origPath);

  }

  // FTP CLIENT COMMANDS ------------------------------------

  public boolean ASCII() {
    if (cmd.length != 1) {
      err.println(logPrefix + "---- Syntax: ASCII  (no parameter)");
      return true;
    }
    try {
      literal("TYPE A");
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: ASCII transfer type not supported by server.");
    }
    return true;
  }

  public boolean BINARY() {
    if (cmd.length != 1) {
      err.println(logPrefix + "---- Syntax: BINARY  (no parameter)");
      return true;
    }
    try {
      literal("TYPE I");
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: BINARY transfer type not supported by server.");
    }
    return true;
  }

  public boolean BYE() {
    return QUIT();
  }
  
  public boolean CD() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: CD <path>");
      return true;
    }
    if (ControlSocket == null) return LCD();
    try {
      // send cwd command
      send("CWD " + cmd[1]);

      String reply = receive();
      if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: change of working directory to path " + cmd[1] + " failed.");
    }
    return true;
  }

  public boolean CLOSE() {
    return DISCONNECT();
  }

  private void rmForced(String path) throws IOException {
    // first try: send DELE command (to delete a file)
    send("DELE " + path);
    // read reply
    String reply1 = receive();
    if (Integer.parseInt(reply1.substring(0, 1)) != 2) {
      // second try: send a RMD command (to delete a directory)
      send("RMD " + path);
      // read reply
      String reply2 = receive();
      if (Integer.parseInt(reply2.substring(0, 1)) != 2) {
      	// third try: test if this thing is a directory or file and send appropriate error message
        if (isFolder(path))
          throw new IOException(reply2);
        else
          throw new IOException(reply1);
      }
    }
  }

  public boolean DEL() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: DEL <file>");
      return true;
    }
    if (ControlSocket == null) return LDEL();
    try {
      rmForced(cmd[1]);
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: deletion of file " + cmd[1] + " failed.");
    }
    return true;
  }


  public boolean RM() {
    return DEL();
  }

  public boolean DIR() {
    if (cmd.length > 2) {
      err.println(logPrefix + "---- Syntax: DIR [<path>|<file>]");
      return true;
    }
    if (ControlSocket == null) return LDIR();
    try {
      Vector l;
      if (cmd.length == 2) l = list(cmd[1],false); else l = list(".",false);
      Enumeration x = l.elements();
      out.println(logPrefix + "---- v---v---v---v---v---v---v---v---v---v---v---v---v---v---v---v---v---v---v");
      while (x.hasMoreElements()) out.println(logPrefix + (String) x.nextElement());
      out.println(logPrefix + "---- ^---^---^---^---^---^---^---^---^---^---^---^---^---^---^---^---^---^---^");
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: remote list not available");
    }
    return true;
  }

  public boolean DISCONNECT() {
    try {
      // send delete command
      send("QUIT");
      
      // read status reply
      String reply = receive();
      if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);

      // cleanup
      if (ControlSocket != null) {
        clientOutput.close();
        clientInput.close();
        ControlSocket.close();
      }

      if (DataSocketActive != null) DataSocketActive.close();
      if (DataSocketPassive != null) DataSocketPassive.close();
      
      out.println(logPrefix + "---- Connection closed.");
    } catch (IOException e) {
      err.println(logPrefix + "---- Connection to server lost.");
    }
    this.account = null;
    this.ControlSocket = null;
    this.DataSocketActive = null;
    this.DataSocketPassive = null;
    this.clientInput = null;
    this.clientOutput = null;
    this.prompt = "ftp [local]>";
    return true;
  }

  public boolean EXIT() {
    return QUIT();
  }


  public boolean GET() {
    if ((cmd.length < 2) || (cmd.length > 3)) {
      err.println(logPrefix + "---- Syntax: GET <remote-file> [<local-file>]");
      return true;
    }
    String remote = cmd[1]; //(new File(cmd[1])).getName();
    File local;
    File l;
    if (cmd.length == 2) {
      l = new File(remote);
      if (l.isAbsolute()) local = l; else local = new File(this.currentPath, remote);
    } else {
      l = new File(cmd[2]);
      if (l.isAbsolute()) local = l; else local = new File(this.currentPath, cmd[2]);
    }
    if (local.exists()) {
      err.println(logPrefix + "---- Error: local file " + local.toString() + " already exists.");
      err.println(logPrefix + "            File " + remote + " not retrieved. Local file unchanged.");
    } else {
      if (cmd.length == 2)
        retrieveFilesRecursively(remote, false);
      else try {
        get(local.getAbsolutePath(), remote);
      } catch (IOException e) {
        err.println(logPrefix + "---- Error: retrieving file " + remote + " failed. (" + e.getMessage() + ")");
      }
    }
    return true;
  }


  private void retrieveFilesRecursively(String remote, boolean delete) {
    File local;
    File l = new File(remote);
    if (l.isAbsolute()) local = l; else local = new File(this.currentPath, remote);
    try {
      get(local.getAbsolutePath(), remote);
      try {if (delete) rmForced(remote);} catch (IOException eee) {
        err.println(logPrefix + "---- Warning: remote file or path " + remote + " cannot be removed.");
      }
    } catch (IOException e) {
      if (e.getMessage().startsWith("550")) {
      // maybe it's a "not a plain file" error message", then it can be a folder
      // test if this exists (then it should be a folder)
      if (isFolder(remote)) {
        // copy the whole directory
        exec("cd \"" + remote + "\";lmkdir \"" + remote + "\";lcd \"" + remote + "\"",true);
        //exec("mget *",true);
        try {
          Enumeration files = list(".",false).elements();
          while (files.hasMoreElements()) retrieveFilesRecursively((String) files.nextElement(), delete);
        } catch (IOException ee) {}
          exec("cd ..;lcd ..", true);
          try {if (delete) rmForced(remote);} catch (IOException eee) {
      	    err.println(logPrefix + "---- Warning: remote file or path " + remote + " cannot be removed.");
          }
        } else {
      	  err.println(logPrefix + "---- Error: remote file or path " + remote + " does not exist.");
      	}
      } else {
        err.println(logPrefix + "---- Error: retrieving file " + remote + " failed. (" + e.getMessage() + ")");
      }
    }
  }

    private boolean isFolder(String path) {
      try {
        send("CWD " + path);
        String reply = receive();
        if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);
        send("CWD ..");
        reply = receive();
        return true;
      } catch (IOException e) {
        return false;
      }
    }

  public boolean GLOB() {
    if (cmd.length != 1) {
      err.println(logPrefix + "---- Syntax: GLOB  (no parameter)");
      return true;
    }
    this.glob = !this.glob;
    out.println(logPrefix + "---- globbing is now turned " + ((this.glob) ? "ON" : "OFF"));
    return true;
  }

  public boolean HASH() {
    err.println(logPrefix + "---- no games implemented");
    return true;
  }

  public boolean JAR() {
    sun.tools.jar.Main.main(shift(cmd));
    return true;
  }

  
  public boolean JJENCODE() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: JJENCODE <path>");
      return true;
    }
    String path = cmd[1];

    File dir = new File(path);
    File newPath = dir.isAbsolute() ? dir : new File(this.currentPath, path);
    if (newPath.exists()) {
      if (newPath.isDirectory()) {
//  exec("cd \"" + remote + "\";lmkdir \"" + remote + "\";lcd \"" + remote + "\"",true);
/*
if not exist %1\nul goto :error
cd %1
c:\jdk1.2.2\bin\jar -cfM0 ..\%1.jar *.*
cd ..
c:\jdk1.2.2\bin\jar -cfM %1.jj %1.jar
del %1.jar
*/
          String s = "";
          String[] l = newPath.list();
          for (int i = 0; i < l.length; i++) s = s + " \"" + l[i] + "\"";
	  exec("cd \"" + path + "\";jar -cfM0 ../\"" + path + ".jar\"" + s, true);
          exec("cd ..;jar -cfM \"" + path + ".jj\" \"" + path + ".jar\"", true);
          exec("rm \"" + path + ".jar\"", true);
      } else {
        err.println(logPrefix + "---- Error: local path " + newPath.toString() + " denotes not to a directory.");
      }
    } else {
      err.println(logPrefix + "---- Error: local path " + newPath.toString() + " does not exist.");    
    }
    return true;
  }

  public boolean JJDECODE() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: JJENCODE <path>");
      return true;
    }
    String path = cmd[1];
    File dir = new File(path);
    File newPath = dir.isAbsolute() ? dir : new File(this.currentPath, path);
    File newFolder = new File(newPath.toString() + ".dir");
    if (newPath.exists()) {
      if (!newPath.isDirectory()) {
	if (!newFolder.mkdir()) {
/*
if not exist %1.jj goto :error
mkdir %1.dir
copy %1.jj %1.dir\ > %1.dummy && del %1.dummy
cd %1.dir
c:\jdk1.2.2\bin\jar -xf %1.jj
del %1.jj
c:\jdk1.2.2\bin\jar -xf %1.jar
del %1.jar
cd ..
*/
	    exec("mkdir \"" + path + ".dir\"", true);
	    
        } else {
          err.println(logPrefix + "---- Error: target dir " + newFolder.toString() + " cannot be created");
	}	    
      } else {
        err.println(logPrefix + "---- Error: local path " + newPath.toString() + " must denote to jar/jar file");
      }
    } else {
      err.println(logPrefix + "---- Error: local path " + newPath.toString() + " does not exist.");    
    }
    return true;
  }

  private static String[] argList2StringArray(String argList) {
    // command line parser
    StringTokenizer tokens = new StringTokenizer(argList);
    String[] args = new String[tokens.countTokens()];
    for (int i = 0; tokens.hasMoreTokens(); i++) args[i] = tokens.nextToken();
    tokens = null; // free mem
    return args;
  }

  public boolean JOIN(String[] args) {

    // make sure the specified dest file does not exist
    String dest_name = args[1];
    File dest_file = new File(dest_name);
    if (dest_file.exists()) {
	err.println(logPrefix + "join: destination file " + dest_name + " already exists");
	return true;
    }

    // prepare or search file names of the input files to be joined
    String source_name;
    File source_file;
    int pc = -1;
    // create new string array with file names
    // scan first for the files
    pc = 0;
    source_name = dest_name + ".000";
    String argString = "";
    source_file = new File(source_name);
    while ((source_file.exists()) && (source_file.isFile()) && (source_file.canRead())) {
      argString = argString + " " + source_name;
      pc++;
      source_name = dest_name + (pc < 10 ? ".00"+pc : (pc < 100 ? ".0"+pc : "."+pc));
      source_file = new File(source_name);
    }
    args = argList2StringArray(argString.substring(1));
    
    // do the join
    FileOutputStream dest = null;
    FileInputStream source = null;        
    byte[] buffer;
    int bytes_read = 0;

    try {
      // open output file
      dest = new FileOutputStream(dest_file);
      buffer = new byte[1024];

      // append all source files      
      for (pc = 0; pc < args.length; pc++) {
        // open the source file
        source_name = args[pc];
        source_file = new File(source_name);
        source = new FileInputStream(source_file);

        // start with the copy of one source file
        while (true) {
          bytes_read = source.read(buffer);
          if (bytes_read == -1) break;
          dest.write(buffer, 0, bytes_read);
        }

        // copy finished. close source file
        if (source != null) try { source.close(); } catch (IOException e) {}
      }
      // close the output file
      if (dest != null) try { dest.close(); } catch (IOException e) {}
      
      // if we come to this point then everything went fine
      // if the user wanted to delete the source it is save to do so now
      for (pc = 0; pc < args.length; pc++) {
        try {
          if (!(new File(args[pc])).delete())
            System.err.println(logPrefix + "join: unable to delete file " + args[pc]);
        } catch (SecurityException e) {
	  System.err.println(logPrefix + "join: no permission to delete file " + args[pc]);
        }
      }
    } catch (FileNotFoundException e) {
    } catch (IOException e) {
    }
    
    // clean up
    finally {
      // close any opened streams
      if (dest != null) try { dest.close(); } catch (IOException e) {}
      if (source != null) try { source.close(); } catch (IOException e) {}
         
      // print appropriate message
      System.err.println(logPrefix + "join created output from " + args.length + " source files");
    }
    return true;
  }

  public boolean COPY(String[] args) {
    File dest_file = new File(args[2]);
    if (dest_file.exists()) {
	err.println(logPrefix + "copy: destination file " + args[2] + " already exists");
	return true;
    }
    int bytes_read = 0;
    try {
      // open output file
      FileOutputStream dest = new FileOutputStream(dest_file);
      byte[] buffer = new byte[1024];

      // open the source file
      File source_file = new File(args[1]);
      FileInputStream source = new FileInputStream(source_file);

      // start with the copy of one source file
      while (true) {
        bytes_read = source.read(buffer);
        if (bytes_read == -1) break;
        dest.write(buffer, 0, bytes_read);
      }

      // copy finished. close source file
      if (source != null) try { source.close(); } catch (IOException e) {}

      // close the output file
      if (dest != null) try { dest.close(); } catch (IOException e) {}
    } catch (FileNotFoundException e) {
    } catch (IOException e) {
    }
    return true;
  }

  public boolean JAVA() {
    String s = "JAVA";
    for (int i = 1; i< cmd.length; i++) s = s + " " + cmd[i];
    try {
      send(s);
      String reply = receive();
    } catch (IOException e) {}
    return true;
  }

  public boolean LCD() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: LCD <path>");
      return true;
    }
    String path = cmd[1];
    File dir = new File(path);
    File newPath = dir.isAbsolute() ? dir : new File(this.currentPath, path);
    try {newPath = new File(newPath.getCanonicalPath());} catch (IOException e) {}
    if (newPath.exists()) {
      if (newPath.isDirectory()) {
        this.currentPath = newPath;
        out.println(logPrefix + "---- New local path: " + this.currentPath.toString());
      } else {
        err.println(logPrefix + "---- Error: local path " + newPath.toString() + " denotes not a directory.");
      }
    } else {
      err.println(logPrefix + "---- Error: local path " + newPath.toString() + " does not exist.");    
    }
    return true;
  }

  public boolean LDEL() {
    return LRM();
  }

  public boolean LDIR() {
    if (cmd.length != 1) {
      err.println(logPrefix + "---- Syntax: LDIR  (no parameter)");
      return true;
    }
    String[] name = this.currentPath.list();
    for (int n = 0; n < name.length; ++ n) out.println(logPrefix + ls(new File(this.currentPath, name[n])));
    return true;
  }
    
    private String ls(File inode) {
      if ((inode == null) || (!inode.exists())) return "";
      String s = "";
      if (inode.isDirectory()) s = s + "d";
      else if (inode.isFile()) s = s + "-";
      //else if (inode.isHidden()) s = s + "h";
      else s = s + "?";
      if (inode.canRead()) s = s + "r"; else s = s + "-";
      if (inode.canWrite()) s = s + "w"; else s = s + "-";
      s = s + " " + lenformatted(Long.toString(inode.length()),9);
      DateFormat df = DateFormat.getDateTimeInstance();
      s = s + " " + df.format(new Date(inode.lastModified()));
      s = s + " " + inode.getName();
      if (inode.isDirectory()) s = s + "/";      
      return s;
    }

    private String lenformatted(String s, int l) {
      l = l - s.length();
      while (l > 0) {s = " " + s; l--;}
      return s;
    }

  public boolean LITERAL() {
    if (cmd.length == 1) {
      err.println(logPrefix + "---- Syntax: LITERAL <ftp-command> [<command-argument>]   (see RFC959)");
      return true;
    }
    String s = "";
    for (int i = 1; i < cmd.length; i++) s = s + " " + cmd[i];
    try {
      literal(s.substring(1));
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: Syntax of FTP-command wrong. See RFC959 for details.");
    }
    return true;
  }

  public boolean LLS() {
    return LDIR();
  }

  public boolean LMD() {
    return LMKDIR();
  }

  public boolean LMKDIR() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: LMKDIR <folder-name>");
      return true;
    }
    File f = new File(this.currentPath, cmd[1]);
    if (f.exists()) {
      err.println(logPrefix + "---- Error: local file/folder " + cmd[1] + " already exists");
    } else {
      if (!f.mkdir()) err.println(logPrefix + "---- Error: creation of local folder " + cmd[1] + " failed");
    }
    return true;
  }
  
  public boolean LMV() {
    if (cmd.length != 3) {
      err.println(logPrefix + "---- Syntax: LMV <from> <to>");
      return true;
    }      
    File from = new File(cmd[1]);
    File to = new File(cmd[2]);
    if (!to.exists()) {
      if (from.renameTo(to)) {
	out.println(logPrefix + "---- \"" + from.toString() + "\" renamed to \"" + to.toString() + "\"");
      } else err.println(logPrefix + "rename failed");
    } else err.println(logPrefix + "\"" + to.toString() + "\" already exists");
    return true;
  }
  
  public boolean LPWD() {
    if (cmd.length != 1) {
      err.println(logPrefix + "---- Syntax: LPWD  (no parameter)");
      return true;
    }
    out.println(logPrefix + "---- Local path: " + this.currentPath.toString());
    return true;
  }

  public boolean LRD() {
    return LMKDIR();
  }

  public boolean LRMDIR() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: LRMDIR <folder-name>");
      return true;
    }
    File f = new File(this.currentPath, cmd[1]);
    if (!f.exists()) {
      err.println(logPrefix + "---- Error: local folder " + cmd[1] + " does not exist");
    } else {
      if (!f.delete()) err.println(logPrefix + "---- Error: deletion of local folder " + cmd[1] + " failed");
    }
    return true;
  }

  public boolean LRM() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: LRM <file-name>");
      return true;
    }
    File f = new File(this.currentPath, cmd[1]);
    if (!f.exists()) {
      err.println(logPrefix + "---- Error: local file " + cmd[1] + " does not exist");
    } else {
      if (!f.delete()) err.println(logPrefix + "---- Error: deletion of file " + cmd[1] + " failed");
    }
    return true;
  }

  public boolean LS() {
    if (cmd.length > 2) {
      err.println(logPrefix + "---- Syntax: LS [<path>|<file>]");
      return true;
    }
    if (ControlSocket == null) return LLS();
    try {
      Vector l;
      if (cmd.length == 2) l = list(cmd[1],true); else l = list(".",true);
      Enumeration x = l.elements();
      out.println(logPrefix + "---- v---v---v---v---v---v---v---v---v---v---v---v---v---v---v---v---v---v---v");
      while (x.hasMoreElements()) out.println(logPrefix + (String) x.nextElement());
      out.println(logPrefix + "---- ^---^---^---^---^---^---^---^---^---^---^---^---^---^---^---^---^---^---^");
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: remote list not available");
    }
    return true;
  }
    
  
  private Vector list(String path, boolean extended) throws IOException {   
    // prepare data channel
    if (DataSocketPassiveMode) createPassiveDataPort(); else createActiveDataPort();

    // send command to the control port
    if (extended)
      send("LIST " + path);
    else
      send("NLST " + path);

    // read status of the command from the control port
    String reply = receive();

    // get status code
    int status = Integer.parseInt(reply.substring(0, 1));

    // starting data transaction
    if (status == 1) {
      Socket data;
      if (DataSocketPassiveMode) {
	data = DataSocketPassive;
      } else {
	data = DataSocketActive.accept();
      }
      BufferedReader ClientStream = new BufferedReader(new InputStreamReader(data.getInputStream()));
      
      // read file system data
      String line;
      int i = 0;
      Vector files = new Vector();
      while ((line = ClientStream.readLine()) != null)
        if (!line.startsWith("total ")) files.addElement(line);

      // after stream is empty we should get control completion echo
      //reply = receive();

      //boolean success = (Integer.parseInt(reply.substring(0, 1)) == 2);

      // shutdown connection
      ClientStream.close();
      data.close();

      //if (!success) throw new IOException(reply);

      files.trimToSize();
      return files;
    } else
     throw new IOException(reply);
  }

  public boolean MDIR() {
    return MKDIR();
  }
  
  public boolean MKDIR() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: MKDIR <folder-name>");
      return true;
    }
    if (ControlSocket == null) return LMKDIR();
    try {
      // send mkdir command
      send("MKD " + cmd[1]);
      // read reply
      String reply = receive();
      if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);     
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: creation of folder " + cmd[1] + " failed");
    }
    return true;
  }
  
  public boolean MGET() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: MGET <file-pattern>");
      return true;
    }
    try {
      mget(cmd[1], false);
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: mget failed (" + e.getMessage() + ")");
    }
    return true;
  }

  private void mget(String pattern, boolean remove) throws IOException {
    Vector l = list(".",false);
    Enumeration x = l.elements();
    String remote;
    File local;
    int idx; // the search for " " is only for improper lists from the server. this fails if the file name has a " " in it
    while (x.hasMoreElements()) {
      remote = (String) x.nextElement();
      //idx = remote.lastIndexOf(" ");
      //if (idx >= 0) remote = remote.substring(idx + 1);
      if (matches(remote, pattern)) {
        local = new File(this.currentPath, remote);
        if (local.exists()) {
          err.println(logPrefix + "---- Warning: local file " + local.toString() + " overwritten.");
          local.delete();
        }
        retrieveFilesRecursively(remote, remove);
      }
    }
  }

  public boolean MOVEDOWN() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: MOVEDOWN <file-pattern>");
      return true;
    }
    try {
      mget(cmd[1], true);
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: movedown failed (" + e.getMessage() + ")");
    }
    return true;
  }
  
/*
  public boolean MOVEUP() {
  }
*/

  public boolean MV() {
    if (cmd.length != 3) {
      err.println(logPrefix + "---- Syntax: MV <from> <to>");
      return true;
    }
    if (ControlSocket == null) return LMV();
    try {
      // send rename commands
      send("RNFR " + cmd[1]);
      // read reply
      String reply = receive();
      if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);    
      send("RNTO " + cmd[2]);
      // read reply
      reply = receive();
      if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);    
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: rename of " + cmd[1] + " to " + cmd[2] + " failed.");
    }
    return true;
  }
  
  public boolean NOOP() {
    if (cmd.length != 1) {
      err.println(logPrefix + "---- Syntax: NOOP  (no parameter)");
      return true;
    }
    try {
      literal("NOOP");
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: server does not know how to do nothing");
    }
    return true;
  }

  public boolean OPEN() {
    if ((cmd.length < 2) || (cmd.length > 3)) {
      err.println(logPrefix + "---- Syntax: OPEN <host> [<port>]");
      return true;
    }
    if (ControlSocket != null) exec("close",false); // close any existing connections first
    int port = 21;
    if (cmd.length == 3) {
      try {
        port = java.lang.Integer.parseInt(cmd[2]);
      } catch (NumberFormatException e) {port = 21;}
    }
    if (cmd[1].indexOf(":") > 0) {
	// port is given
	port = java.lang.Integer.parseInt(cmd[1].substring(cmd[1].indexOf(":") + 1));
	cmd[1] = cmd[1].substring(0,cmd[1].indexOf(":"));
    }
    try {
      ControlSocket = new Socket(cmd[1], port);
      clientInput  = new BufferedReader(new InputStreamReader(ControlSocket.getInputStream()));
      clientOutput = new DataOutputStream(new BufferedOutputStream(ControlSocket.getOutputStream()));

      // read greeting
      receive();    
      out.println(logPrefix + "---- Connection to " + cmd[1] + " established.");
      prompt = "ftp [" + cmd[1] + "]>";
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: connecting " + cmd[1] + " on port " + port + " failed.");
    }
    return true;
  }

  public boolean PROMPT() {
    err.println(logPrefix + "---- prompt is always off");
    return true;
  }

  public boolean PUT() {
    if ((cmd.length < 2) || (cmd.length > 3)) {
      err.println(logPrefix + "---- Syntax: PUT <local-file> [<remote-file>]");
      return true;
    }
    File local = new File(this.currentPath, cmd[1]);
    String remote = (cmd.length == 2) ? local.getName() : cmd[2];
    if (!local.exists()) {
      err.println(logPrefix + "---- Error: local file " + local.toString() + " does not exist.");
      err.println(logPrefix + "            Remote file " + remote + " not overwritten.");
    } else {
      try {
        put(local.getAbsolutePath(), remote);
      } catch (IOException e) {
        err.println(logPrefix + "---- Error: transmitting file " + local.toString() + " failed.");
      }
    }
    return true;
  }

  public boolean PWD() {
    if (cmd.length > 1) {
      err.println(logPrefix + "---- Syntax: PWD  (no parameter)");
      return true;
    }
    if (ControlSocket == null) return LPWD();
    try {
      // send pwd command
      send("PWD");

      // read current directory
      String reply = receive();
      if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);

      // parse directory name out of the reply
      reply = reply.substring(5);
      reply = reply.substring(0, reply.lastIndexOf('"'));      

      out.println(logPrefix + "---- Current remote path is: " + reply);
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: remote path not available");
    }
    return true;
  }

  public boolean REMOTEHELP() {
    if (cmd.length != 1) {
      err.println(logPrefix + "---- Syntax: REMOTEHELP  (no parameter)");
      return true;
    }
    try {
      literal("HELP");
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: remote help not supported by server.");
    }
    return true;
  }

  public boolean RMDIR() {
    if (cmd.length != 2) {
      err.println(logPrefix + "---- Syntax: RMDIR <folder-name>");
      return true;
    }
    if (ControlSocket == null) return LRMDIR();
    try {
      rmForced(cmd[1]);
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: deletion of folder " + cmd[1] + " failed.");
    }
    return true;
  }

  public boolean QUIT() {
    if (ControlSocket != null) exec("close",false);
    return false;
  }
  
  public boolean RECV() {
    return GET();
  }

  public boolean USER() {
    if (cmd.length != 3) {
      err.println(logPrefix + "---- Syntax: USER <user-name> <password>");
      return true;
    }
    try {
      out.println(logPrefix + "---- Granted access for user " + login(cmd[1], cmd[2]) + ".");
    } catch (IOException e) {
      err.println(logPrefix + "---- Error: authorization of user " + cmd[1] + " failed.");
    }
    return true;
  }
  
  public boolean APPEND() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean HELP() {
    out.println(logPrefix + "---- ftp HELP ----");
    out.println(logPrefix + "");
    out.println(logPrefix + "This ftp client shell can act as command shell for the local host as well for the");
    out.println(logPrefix + "remote host. Commands that point to the local host are preceded by 'L'.");
    out.println(logPrefix + "");
    out.println(logPrefix + "Supported Commands:");
    out.println(logPrefix + "ASCII");
    out.println(logPrefix + "   switch remote server to ASCII transfer mode");
    out.println(logPrefix + "BINARY");
    out.println(logPrefix + "   switch remote server to BINARY transfer mode");
    out.println(logPrefix + "BYE");
    out.println(logPrefix + "   quit the command shell (same as EXIT)");
    out.println(logPrefix + "CD <path>");
    out.println(logPrefix + "   change remote path");
    out.println(logPrefix + "CLOSE");
    out.println(logPrefix + "   close connection to remote host (same as DISCONNECT)");
    out.println(logPrefix + "DEL <file>");
    out.println(logPrefix + "   delete file on remote server (same as RM)");
    out.println(logPrefix + "RM <file>");
    out.println(logPrefix + "   remove file from remote server (same as DEL)");
    out.println(logPrefix + "DIR [<path>|<file>] ");
    out.println(logPrefix + "   print file information for remote directory or file");
    out.println(logPrefix + "DISCONNECT");
    out.println(logPrefix + "   disconnect from remote server (same as CLOSE)");
    out.println(logPrefix + "EXIT");
    out.println(logPrefix + "   quit the command shell (same as BYE)");
    out.println(logPrefix + "GET <remote-file> [<local-file>]");
    out.println(logPrefix + "   load <remote-file> from remote server and store it locally,");
    out.println(logPrefix + "   optionally to <local-file>. if the <remote-file> is a directory,");
    out.println(logPrefix + "   then all files in that directory are retrieved,");
    out.println(logPrefix + "   including recursively all subdirectories.");
    out.println(logPrefix + "GLOB");
    out.println(logPrefix + "   toggles globbing: matching with wild cards or not");
    out.println(logPrefix + "COPY");
    out.println(logPrefix + "   copies local files");
    out.println(logPrefix + "LCD <path>");
    out.println(logPrefix + "   local directory change");
    out.println(logPrefix + "LDEL <file>");
    out.println(logPrefix + "   local file delete");
    out.println(logPrefix + "LDIR");
    out.println(logPrefix + "   shows local directory content");
    out.println(logPrefix + "LITERAL <ftp-command> [<command-argument>]");
    out.println(logPrefix + "   Sends FTP commands as documented in RFC959");
    out.println(logPrefix + "LLS");
    out.println(logPrefix + "   as LDIR");
    out.println(logPrefix + "LMD");
    out.println(logPrefix + "   as LMKDIR");
    out.println(logPrefix + "LMV <local-from> <local-to>");
    out.println(logPrefix + "   copies local files");
    out.println(logPrefix + "LPWD");
    out.println(logPrefix + "   prints local path");
    out.println(logPrefix + "LRD");
    out.println(logPrefix + "   as LMKDIR");
    out.println(logPrefix + "LRMD <folder-name>");
    out.println(logPrefix + "   deletes local directory <folder-name>");
    out.println(logPrefix + "LRM <file-name>");
    out.println(logPrefix + "   deletes local file <file-name>");
    out.println(logPrefix + "LS [<path>|<file>]");
    out.println(logPrefix + "   prints list of remote directory <path> or information of file <file>");
    out.println(logPrefix + "MDIR");
    out.println(logPrefix + "   as MKDIR");
    out.println(logPrefix + "MGET <file-pattern>");
    out.println(logPrefix + "   copies files from remote server that fits into the");
    out.println(logPrefix + "   pattern <file-pattern> to the local path.");
    out.println(logPrefix + "MOVEDOWN <file-pattern>");
    out.println(logPrefix + "   copies files from remote server as with MGET");
    out.println(logPrefix + "   and deletes them afterwards on the remote server");
    out.println(logPrefix + "MV <from> <to>");
    out.println(logPrefix + "   moves or renames files on the local host");
    out.println(logPrefix + "NOOP");
    out.println(logPrefix + "   sends the NOOP command to the remote server (which does nothing)");
    out.println(logPrefix + "   This command is usually used to measure the speed of the remote server.");
    out.println(logPrefix + "OPEN <host[':'port]> [<port>]");
    out.println(logPrefix + "   connects the ftp shell to the remote server <host>. Optionally,");
    out.println(logPrefix + "   a port number can be given, the default port number is 21.");
    out.println(logPrefix + "   Example: OPEN localhost:2121 or OPEN 192.168.0.1 2121");
    out.println(logPrefix + "PROMPT");
    out.println(logPrefix + "   compatibility command, that usually toggles beween prompting on or off.");
    out.println(logPrefix + "   ftp has prompting switched off by default and cannot switched on.");
    out.println(logPrefix + "PUT <local-file> [<remote-file>]");
    out.println(logPrefix + "   copies the <local-file> to the remote server to the current remote path or");
    out.println(logPrefix + "   optionally to the given <remote-file> path.");
    out.println(logPrefix + "PWD");
    out.println(logPrefix + "   prints current path on the remote server.");
    out.println(logPrefix + "REMOTEHELP");
    out.println(logPrefix + "   asks the remote server to print the help text of the remote server");
    out.println(logPrefix + "RMDIR <folder-name>");
    out.println(logPrefix + "   removes the directory <folder-name> on the remote server");
    out.println(logPrefix + "QUIT");
    out.println(logPrefix + "   exits the ftp application");
    out.println(logPrefix + "RECV");
    out.println(logPrefix + "   as GET");
    out.println(logPrefix + "USER <user-name> <password>");
    out.println(logPrefix + "   logs into the remote server with the user <user-name>");
    out.println(logPrefix + "   and the password <password>");
    out.println(logPrefix + "");
    out.println(logPrefix + "");
    out.println(logPrefix + "EXAMPLE:");
    out.println(logPrefix + "a standard sessions looks like this");
    out.println(logPrefix + ">open 192.168.0.1:2121");
    out.println(logPrefix + ">user anonymous bob");
    out.println(logPrefix + ">pwd");
    out.println(logPrefix + ">ls");
    out.println(logPrefix + ">.....");
    out.println(logPrefix + "");
    out.println(logPrefix + "");
    return true;
  }
  public boolean QUOTE() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean BELL() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean MDELETE() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean SEND() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean DEBUG() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean MLS() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean TRACE() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean MPUT() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean TYPE() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }
  public boolean CREATE() {
    err.println(logPrefix + "---- not yet supported");
    return true;
  }


  // helper functions
  
  private boolean matches(String name, String pattern) {
    // checks whether the string name matches with the pattern
    // the pattern may contain characters '*' as wildcard for several
    // characters (also none) and '?' to match exactly one characters
    //out.println(logPrefix + "MATCH " + name + " " + pattern);
    if (!this.glob) return name.equals(pattern);
    if (pattern.equals("*")) return true;
    if ((pattern.startsWith("*")) && (pattern.endsWith("*")))
      return // avoid recursion deadlock
       ((matches(name, pattern.substring(1))) ||
        (matches(name, pattern.substring(0, pattern.length() - 1))));
    try {
      int i = pattern.indexOf("?");
      if (i >= 0) {
        if (!(matches(name.substring(0, i), pattern.substring(0, i)))) return false;
        return (matches(name.substring(i + 1), pattern.substring(i + 1)));
      }
      i = pattern.indexOf("*");
      if (i >= 0) {
        if (!(name.substring(0, i).equals(pattern.substring(0, i)))) return false;
        if (pattern.length() == i + 1) return true; // pattern would be '*'
        return (matches(
                  reverse(name.substring(i)),
                  reverse(pattern.substring(i + 1)) + "*"));
      }
      return name.equals(pattern);
    } catch (java.lang.StringIndexOutOfBoundsException e) {
      // this is normal. it's a lazy implementation
      return false;
    }
  }

  private String reverse(String s) {
    if (s.length() < 2) return s;
    return reverse(s.substring(1)) + s.charAt(0);
  }
  

  // protocoll socket commands
  
  private void send(String buf) throws IOException {
    clientOutput.writeBytes(buf);
    clientOutput.write('\r');
    clientOutput.write('\n');
    clientOutput.flush();
    if (buf.startsWith("PASS")) {
	out.println(logPrefix + "> PASS ********");
    } else {
	out.println(logPrefix + "> " + buf);
    }
  }

  private String receive() throws IOException {
    // last reply starts with 3 digit number followed by space
    String reply;

    while(true) {  
      reply = clientInput.readLine();
      
      // sanity check
      if (reply == null) throw new IOException("Server has presumably shut down the connection.");

      out.println(logPrefix + "< " + reply);
      //serverResponse.addElement(reply);
      
      if (reply.length() >= 4 &&
          Character.isDigit(reply.charAt(0)) &&
          Character.isDigit(reply.charAt(1)) &&
          Character.isDigit(reply.charAt(2)) &&
          (reply.charAt(3) == ' '))
        break;  // end of reply
    }
    // return last reply line
    return reply;
  }
  

  private void sendTransferType(char type) throws IOException {
    send("TYPE " + type);

    String reply = receive();
    if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);
  }
 

  private void createActiveDataPort() throws IOException {
    // create data socket and bind it to free port available
    DataSocketActive = new ServerSocket(0);

    // get port socket has been bound to
    int DataPort = DataSocketActive.getLocalPort();

    // client ip
    //InetAddress LocalIp = serverCore.publicIP();
    //    InetAddress LocalIp = DataSocketActive.getInetAddress().getLocalHost();
      
    // save ip address in high byte order
    //byte[] Bytes = LocalIp.getAddress();
    byte[] Bytes = serverCore.publicIP().getBytes();

    // bytes greater than 127 should not be printed as negative
    short[] Shorts = new short[4];
    for (int i = 0; i < 4; i++) {
      Shorts[i] = Bytes[i];
      if (Shorts[i] < 0) Shorts[i] += 256;
    }

    // send port command via control socket: 
    // four ip address shorts encoded and two port shorts encoded
    send("PORT " + 
         //"127,0,0,1," +
         Shorts[0] + "," + Shorts[1] + "," + Shorts[2] + "," + Shorts[3] + "," +
         ((DataPort & 0xff00) >> 8)  + "," + (DataPort & 0x00ff));

    // read status of the command from the control port
    String reply = receive();

    // check status code
    if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);

    DataSocketPassiveMode = false;
  }

  private void createPassiveDataPort() throws IOException {
    // send port command via control socket: 
    // four ip address shorts encoded and two port shorts encoded
    send("PASV");

    // read status of the command from the control port
    String reply = receive();

    // check status code
    if (!(reply.substring(0, 3).equals("227"))) throw new IOException(reply);

    // parse the status return: address should start at the first number
    int pos = 4;
    while ((pos < reply.length()) && ((reply.charAt(pos) < '0') || (reply.charAt(pos) > '9'))) pos++;
    if (pos >= reply.length()) throw new IOException(reply + " [could not parse return code]");
    reply = reply.substring(pos); pos = reply.length() - 1;
    while ((pos >= 0) && ((reply.charAt(pos) < '0') || (reply.charAt(pos) > '9'))) pos--;
    if (pos < 0) throw new IOException("[could not parse return code: no numbers]");
    reply = reply.substring(0, pos + 1);
    StringTokenizer st = new StringTokenizer(reply, ",");
    if (st.countTokens() != 6) throw new IOException("[could not parse return code: wrong number of numbers]");

    // set the data host and port
    int a = Integer.parseInt(st.nextToken());
    int b = Integer.parseInt(st.nextToken());
    int c = Integer.parseInt(st.nextToken());
    int d = Integer.parseInt(st.nextToken());
    InetAddress datahost = InetAddress.getByName(a + "." + b + "." + c + "." + d);
    int high = Integer.parseInt(st.nextToken());
    int low = Integer.parseInt(st.nextToken());
    if (high < 0 || high > 255 || low < 0 || low > 255)  throw new IOException("[could not parse return code: syntax error]");
    int dataport = (high << 8) + low;

    DataSocketPassive = new Socket(datahost, dataport);
    DataSocketPassiveMode = true;
  }

  private void get(String fileDest, String fileName) throws IOException {
    // store time for statistics
    long start = GregorianCalendar.getInstance(GMTTimeZone).getTime().getTime();

    // prepare data channel
    if (DataSocketPassiveMode) createPassiveDataPort(); else createActiveDataPort();
      
    // set type of the transfer
    sendTransferType(transferType);

    // send command to the control port
    send("RETR " + fileName);
      
    // read status of the command from the control port
    String reply = receive();

    // get status code
    int status = Integer.parseInt(reply.substring(0, 1));
      
    // starting data transaction
    if (status == 1) {
      Socket data;
      if (DataSocketPassiveMode) {
	  data = DataSocketPassive;
      } else {
	  data = DataSocketActive.accept();
      }
      InputStream ClientStream = data.getInputStream();

      // create local file
      RandomAccessFile outFile;
      if (fileDest == null)
        outFile = new RandomAccessFile(fileName, "rw");
      else
        outFile = new RandomAccessFile(fileDest, "rw"); 
   
      // write remote file to local file
      byte[] block = new byte[blockSize];
      int numRead;
      long length = 0;
      
      while ((numRead = ClientStream.read(block)) != -1) {
      	outFile.write(block, 0, numRead);
        length = length + numRead;
      }
      
      // after stream is empty we should get control completion echo
      //reply = receive();
      //boolean success = (Integer.parseInt(reply.substring(0, 1)) == 2);
   
      // shutdown connection
      outFile.close();
      ClientStream.close();
      data.close();

      //if (!success) throw new IOException(reply);

      // write statistics
      long stop = GregorianCalendar.getInstance(GMTTimeZone).getTime().getTime();
      out.print("---- downloaded " +
        ((length < 2048) ? length + " bytes" : ((int) length / 1024) + " kbytes") +
        " in " +
        (((stop - start) < 2000) ? (stop - start) + " milliseconds" : (((int) ((stop - start) / 100)) / 10) + " seconds"));
      if (start == stop) err.println(logPrefix + ""); else
        out.println(logPrefix + " (" + ((long) (length * 1000 / 1024 / (stop - start))) + " kbytes/second)");

    } else
      throw new IOException(reply);
  }

  private void put(String fileName, String fileDest) throws IOException {

    // prepare data channel
    if (DataSocketPassiveMode) createPassiveDataPort(); else createActiveDataPort();

    // set type of the transfer
    sendTransferType(transferType);
    
    // send command to the control port
    if (fileDest == null)
      send("STOR " + fileName);
    else
      send("STOR " + fileDest);

    // read status of the command from the control port
    String reply = receive();

    // starting data transaction
    if (Integer.parseInt(reply.substring(0, 1)) == 1) {   
      // ftp server initiated client connection
      Socket data;
      if (DataSocketPassiveMode) {
	  data = DataSocketPassive;
      } else {
	  data = DataSocketActive.accept();
      }
      OutputStream ClientStream = data.getOutputStream();

      // read from local file
      RandomAccessFile inFile = new RandomAccessFile(fileName, "r");

      // write remote file to local file
      byte[] block = new byte[blockSize];
      int numRead;

      while ((numRead = inFile.read(block)) >= 0) {
        ClientStream.write(block, 0, numRead);
      }

      // shutdown and cleanup
      inFile.close();
      ClientStream.close();

      // after stream is empty we should get control completion echo
      reply = receive();
      boolean success = (Integer.parseInt(reply.substring(0, 1)) == 2);

      // shutdown remote client connection
      data.close();

      if (!success) throw new IOException(reply);

    } else
      throw new IOException(reply);
  }


  private String login(String account, String password) throws IOException {

    // send user name
    send("USER " + account);

    String reply = receive();
    if (Integer.parseInt(reply.substring(0, 1)) == 4) throw new IOException(reply);
    if (Integer.parseInt(reply.substring(0, 1)) == 2) return this.account = account;

    // send password
    send("PASS " + password);

    reply = receive();
    if (Integer.parseInt(reply.substring(0, 1)) != 2) throw new IOException(reply);      

    this.account = account;
    return account;
  }


  private String login() throws IOException {
    // force anonymous login if not already connected
    if (this.account == null) {
      login("anonymous", "bob@");
      return this.account;
    } else
      return this.account;
  }

  private String sys() throws IOException {
    // send system command
    send("SYST");

    // check completion
    String systemType = receive();
    if (Integer.parseInt(systemType.substring(0, 1)) != 2) throw new IOException(systemType);

    // exclude status code from reply 
    return systemType.substring(4);
  }


  private void literal(String commandLine) throws IOException {
    // send the complete line
    send(commandLine);

    // read reply
    String reply = receive();

    if (Integer.parseInt(reply.substring(0, 1)) == 5) throw new IOException(reply);    
  }

 class ee extends SecurityException {
    private int value = 0;
    public ee() {}
    public ee(int value) {
        super();
        this.value = value;
    }
    public int value() { return value; }
 }
 
 class sm extends SecurityManager {
    public void checkCreateClassLoader() { }
    public void checkAccess(Thread g) { }
    public void checkAccess(ThreadGroup g) { }
    public void checkExit(int status) {
        //System.out.println(logPrefix + "ShellSecurityManager: object called System.exit(" + status + ")");
        // signal that someone is trying to terminate the JVM.
        throw new ee(status);
    }
    public void checkExec(String cmd) { }
    public void checkLink(String lib) { }
    public void checkRead(FileDescriptor fd) { }
    public void checkRead(String file) { }
    public void checkRead(String file, Object context) { }
    public void checkWrite(FileDescriptor fd) { }
    public void checkWrite(String file) { }
    public void checkDelete(String file) { }
    public void checkConnect(String host, int port) { }
    public void checkConnect(String host, int port, Object context) { }
    public void checkListen(int port) { }
    public void checkAccept(String host, int port) { }
    public void checkMulticast(InetAddress maddr) { }
     //public void checkMulticast(InetAddress maddr, byte ttl) { }
    public void checkPropertiesAccess() { }
    public void checkPropertyAccess(String key) { }
    public void checkPropertyAccess(String key, String def) { }
    public boolean checkTopLevelWindow(Object window) { return true; }
    public void checkPrintJobAccess() { }
    public void checkSystemClipboardAccess() { }
    public void checkAwtEventQueueAccess() { }
    public void checkPackageAccess(String pkg) { }
    public void checkPackageDefinition(String pkg) { }
    public void checkSetFactory() { }
    public void checkMemberAccess(Class clazz, int which) { }
    public void checkSecurityAccess(String provider) { }
 }

    
    public static Vector dir(String host,
			   String remotePath,
			   String account, String password,
                           boolean extended) {
	try {
	    ftpc c = new ftpc();
            c.cmd = new String[]{"open", host}; c.OPEN();
            c.cmd = new String[]{"user", account, password}; c.USER();
            c.cmd = new String[]{"ls"}; Vector v = c.list(remotePath, extended);
            c.cmd = new String[]{"close"}; c.CLOSE();
            c.cmd = new String[]{"exit"}; c.EXIT();
            return v;
	} catch (java.security.AccessControlException e) {
            return null;
	} catch (IOException e) {
            return null;
	}
    }
 
    public static void dir(String host,
			   String remotePath,
			   String account, String password) {
	try {
	    ftpc c = new ftpc();
	    c.exec("open " + host, false);
	    c.exec("user " + account + " " + password, false);
	    c.exec("cd " + remotePath, false);
	    c.exec("ls", true);
	    c.exec("close", false);
	    c.exec("exit", false);
	} catch (java.security.AccessControlException e) {
	}
    }

    public static void dirAnonymous(String host,
				    String remotePath) {
	dir(host, remotePath, "anonymous", "anomic");
    }

    public static String put(String host,
            File localFile, String remotePath, String remoteName,
            String account, String password) throws IOException {
        // returns the log
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(bout);
            
            ByteArrayOutputStream berr = new ByteArrayOutputStream();
            PrintStream err = new PrintStream(berr);            
            
            ftpc c = new ftpc(System.in, out, err);
            c.exec("open " + host, false);
            c.exec("user " + account + " " + password, false);
            if (remotePath != null) {
                remotePath = remotePath.replace('\\', '/');
                c.exec("cd " + remotePath, false);
            }
            c.exec("binary", false);
            if (localFile.isAbsolute()) {
                c.exec("lcd " + localFile.getParent(),false);
                localFile = new File(localFile.getName());
            }
            c.exec("put " + localFile.toString() + ((remoteName.length() == 0) ? "" : (" " + remoteName)), false);
            c.exec("close", false);
            c.exec("exit", false);
            
            out.close();
            err.close();
            
            String outLog = bout.toString();
            bout.close();
            
            String errLog = berr.toString();
            berr.close();
            
            if (errLog.length() > 0) {
                throw new IOException ("Ftp put failed:\n" + errLog);
            }
            
            return outLog;
        } catch (IOException e) {
            throw e;
        }
    }

    public static void get(String host,
			   String remoteFile, File localPath,
			   String account, String password) {
	try {
	    ftpc c = new ftpc();
            if (remoteFile.length() == 0) remoteFile = "/";
	    c.exec("open " + host, false);
	    c.exec("user " + account + " " + password, false);
	    c.exec("lcd " + localPath.getAbsolutePath().toString(), false);
	    c.exec("binary", false);
	    c.exec("get " + remoteFile + " " + localPath.getAbsoluteFile().toString(), false);
	    c.exec("close", false);
	    c.exec("exit", false);
	} catch (java.security.AccessControlException e) {
	}
    }

    public static void getAnonymous(String host,
				    String remoteFile, File localPath) {
	get(host, remoteFile, localPath, "anonymous", "anomic");
    }


    public static class pt implements Runnable {
	String host;
	File localFile;
	String remotePath;
	String remoteName;
	String account;
	String password;
	public pt(String h, File l, String rp, String rn, String a, String p) {
	    host = h; localFile = l; remotePath = rp; remoteName = rn; account = a; password = p;
	}
    public final void run() {
        try {
            put(host, localFile, remotePath, remoteName, account, password);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    }

    public static Thread putAsync(String host,
				  File localFile, String remotePath, String remoteName,
				  String account, String password) {
	Thread t = new Thread(new pt(host, localFile, remotePath, remoteName, account, password));
	t.start();
	return t; // return value can be used to determine status of transfer with isAlive() or join()
    }

    private static void printHelp() {
	System.out.println(logPrefix + "ftp help");
	System.out.println(logPrefix + "----------");
	System.out.println(logPrefix + "");
	System.out.println(logPrefix + "The following commands are supported");
	System.out.println(logPrefix + "java ftp  -- (without arguments) starts the shell. Thy 'help' then for shell commands.");
	System.out.println(logPrefix + "java ftp <host>[':'<port>]  -- starts shell and connects to specified host");
	System.out.println(logPrefix + "java ftp -h  -- prints this help");
	System.out.println(logPrefix + "java ftp -dir <host>[':'<port>] <path> [<account> <password>]");
	System.out.println(logPrefix + "java ftp -get <host>[':'<port>] <remoteFile> <localPath> [<account> <password>]");
	System.out.println(logPrefix + "java ftp -put <host>[':'<port>] <localFile> <remotePath> <account> <password>");
	System.out.println(logPrefix + "");
    }

    public static void main(String[] args) {
	System.out.println(logPrefix + "WELCOME TO THE ANOMIC FTP CLIENT v" + vDATE);
	System.out.println(logPrefix + "Visit http://www.anomic.de and support shareware!");
	System.out.println(logPrefix + "try -h for command line options");
	System.out.println(logPrefix + "");
	if (args.length == 0) {
	    (new ftpc()).shell(null);
	} else if (args.length == 1) {
	    if (args[0].equals("-h")) {
		printHelp();
	    } else {
		(new ftpc()).shell(args[0]);
	    }
	} else if (args.length == 2) {
	    printHelp();
	} else if (args.length == 3) {
	    if (args[0].equals("-dir")) {
		dirAnonymous(args[1], args[2]);
	    } else {
		printHelp();
	    }
	} else if (args.length == 4) {
	    if (args[0].equals("-get")) {
		getAnonymous(args[1], args[2], new File(args[3]));
	    } else {
		printHelp();
	    }
	} else if (args.length == 5) {
	    if (args[0].equals("-dir")) {
		dir(args[1], args[2], args[3], args[4]);
	    } else {
		printHelp();
	    }
	} else if (args.length == 6) {
	    if (args[0].equals("-get")) {
		get(args[1], args[2], new File(args[3]), args[4], args[5]);
	    } else if (args[0].equals("-put")) {
            try {
                put(args[1], new File(args[2]), args[3], "", args[4], args[5]);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
	    } else {
		printHelp();
	    }
	} else {
	    printHelp();
	}
    }

}
