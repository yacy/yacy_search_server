//ShareService.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file was contributed by Martin Thelian
//
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


package de.anomic.soap.services;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;

import org.apache.axis.AxisFault;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.attachments.Attachments;
import org.w3c.dom.Document;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSystem;
import de.anomic.soap.AbstractService;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class ShareService extends AbstractService {

	private static final int FILEINFO_MD5_STRING = 0;
	private static final int FILEINFO_COMMENT = 1;
	
	private static final int GENMD5_MD5_ARRAY = 0;
	private static final int GENMD5_MD5_STRING = 1;
	
	/* =====================================================================
	 * Used XML Templates
	 * ===================================================================== */	
    private static final String TEMPLATE_SHARE_XML = "htdocsdefault/dir.xml";
    
    /**
     * @return the yacy htroot directory
     * @throws AxisFault if the directory does not exist
     */
    private File getHtrootDir() throws AxisFault {
    	// get htroot path
    	File htroot = new File(this.switchboard.getRootPath(), this.switchboard.getConfig("htDocsPath", "DATA/HTDOCS"));
    	if (!htroot.exists()) throw new AxisFault("htroot directory does not exists.");
    	return htroot;
    }
    
    /**
     * @return the yacy fileshare directory
     * @throws AxisFault if the directory does not exist
     */
    private File getShareDir() throws AxisFault {
    	File htroot = getHtrootDir();    	
    	File share = new File(htroot,"share/");
    	if (!share.exists()) throw new AxisFault("Share directory does not exists.");
    	return share;
    }
    
    /**
     * Converts the relative path received as input parameter into an absolut
     * path pointing to a location in the yacy file-share.
     * @param path the relative path
     * @return the absolut path
     * 
     * @throws AxisFault if the directory does not exist
     * @throws AxisFault if the directory is not readable
     * @throws AxisFault if the directory path is too long
     * @throws AxisFault if the directory path is outside of the yacy share directory
     * @throws IOException other io errors
     */
    private File getWorkingDir(String path) throws IOException {
    	File share = getShareDir();
    	
    	if (path != null && path.startsWith("/")) path = path.substring(1);
    	
    	// construct directory
    	File workingDir = (path==null)?share:new File(share,path);
    	
    	if (!workingDir.exists())
    		throw new AxisFault("Working directory does not exists");
    	
    	if (!workingDir.canRead())
    		throw new AxisFault("Working directory is not readable.");
    	
    	if (workingDir.getAbsolutePath().length() > serverSystem.maxPathLength) 
    		throw new AxisFault("Path name is too long");
    	
    	if (!workingDir.getCanonicalPath().startsWith(share.getCanonicalPath())) 
    		throw new AxisFault("Invalid path. Path does not start with " + share.getCanonicalPath());
    	
    	return workingDir;
    }
    
    /**
     * Returns a file object representing a file in the yacy fileshare directory
     * @param workingDir the current working directory
     * @param workingFileName the name of the file
     * @return a file object pointing to a file or directory in the yacy fileshare directory
     * 
     * @throws NullPointerException if the filename is null
     * @throws AxisFault if the file path is too long
     * @throws AxisFault if the file path is outside the yacy share directory
     * @throws AxisFault if the file path is pointing to share itself
     * 
     * @throws IOException on other io errors
     */
    private File getWorkingFile(File workingDir, String workingFileName) throws AxisFault, IOException {
    	if (workingDir == null) throw new NullPointerException("Working dir is null");
    	
    	// getting file-share directory
    	File share = getShareDir();
    	
    	// check filename for illegal characters
    	if (workingFileName != null) {
    		if ((workingFileName.indexOf("/") != -1) || (workingFileName.indexOf("/") != -1))
    			throw new AxisFault("Filename contains illegal characters.");    			
    	}
    	
        File workingFile = (workingFileName==null)?workingDir:new File(workingDir, workingFileName);
        
    	if (workingFile.getAbsolutePath().length() > serverSystem.maxPathLength) 
    		throw new AxisFault("Path name is too long");
    	
    	if (!workingFile.getCanonicalPath().startsWith(workingDir.getCanonicalPath())) 
    		throw new AxisFault("Invalid path. Path does not start with " + workingDir.getCanonicalPath());
    	
    	if (share.getCanonicalPath().equals(workingFile.getCanonicalPath()))
    		throw new AxisFault("Invalid path. You can not operate on htroot.");
    	
    	return workingFile;
    }
    
    /**
     * Returns the md5 sum of a file
     * @param theFile the file for which the MD5 sum should be calculated
     * @return the md5 sum as byte array
     */
    private byte[] generateFileMD5(File theFile) {
    	byte[] md5 = serverCodings.encodeMD5Raw(theFile);
    	return md5;
    }
    
    /**
     * Returns the hex. representation of a md5 sum array
     * @param md5Array the md5 sum as byte array
     * @return the string representation of the md5 sum
     */
    private String convertMD5ArrayToString(byte[] md5Array) {        
        String md5s = serverCodings.encodeHex(md5Array); 
        return md5s;
    }
    
    /**
     * Returns a file object representing the md5-file that belongs to a regular yacy fileshare file
     * @param theFile the original file
     * @return the md5 file that belongs to the original file
     * 
     * @throws IOException
     */
    private File getFileMD5File(File theFile) throws IOException {
    	final File md5File = new File(theFile.getCanonicalPath() + ".md5");   
    	return md5File;
    }
    
    /**
     * Generates a md5 sum of a file and store it together with an optional comment
     * in a special md5 file.
     * @param theFile the original file
     * @param comment description of the file
     * @return an Object array containing
     * <ul>
     * 	<li>[0] the md5 sum of the file as byte array</li>
     * 	<li>[1] the md5 sum of the file as string</li>
     * </ul>
     * @throws UnsupportedEncodingException should never occur
     * @throws IOException if the md5 file could not be written or the source file could not be read 
     */
    private Object[] generateMD5File(File theFile, String comment) throws UnsupportedEncodingException, IOException {
    	if (comment == null) comment = "";
    	
    	// calculate md5
		byte[] md5b = generateFileMD5(theFile);
		
		// convert md5 sum to string
		String md5s = convertMD5ArrayToString(md5b);
		
		// write comment + md5 to file
		File md5File = getFileMD5File(theFile);
		if (md5File.exists()) md5File.delete();
        serverFileUtils.write((md5s + "\n" + comment).getBytes("UTF-8"), md5File);
        
        return new Object[]{md5b,md5s};
    }
    
    /**
     * Returns the content of the md5-file that belongs to a regular yacy file-share file
     * @param theFile the regular file-share file
     * @return an array containing
     * <ul>
     * 	<li>[0] the md5 sum of the file as string</li>
     *  <li>[1] the comment</li>
     * </ul>
     * @throws IOException if the md5 file could not be read
     */
    private String[] getFileInfo(File theFile) throws IOException {
    	File md5File = getFileMD5File(theFile);

    	String md5s = "";
    	String description = "";
    	if (md5File.exists()) {
    		try {    			
    			md5s = new String(serverFileUtils.read(md5File),"UTF-8");
    			int pos = md5s.indexOf('\n');
    			if (pos >= 0) {
    				description = md5s.substring(pos + 1);
    				md5s = md5s.substring(0, pos);
    			}
    		} catch (IOException e) {/* */} 
    	}
    	return new String[]{md5s,description};
    }
    
    private String yacyhURL(yacySeed seed, String filename, String md5) throws AxisFault {
    	try {
    		// getting the template class file
    		Class c = this.serverContext.getProvider().loadClass(this.serverContext.getServletClassFile(TEMPLATE_SHARE_XML));
    		Method m = c.getMethod("yacyhURL", new Class[]{yacySeed.class,String.class,String.class});
    		
    		// invoke the desired method
    		return (String) m.invoke(null, new Object[] {seed,filename,md5});
    	} catch (Exception e) {
    		throw new AxisFault("Unable to generate the yacyhURL");
    	}
    }
    
    private void indexPhrase(String urlstring, String phrase, String descr, byte[] md5) throws AxisFault {
    	try {
    		// getting the template class file
    		Class c = this.serverContext.getProvider().loadClass(this.serverContext.getServletClassFile(TEMPLATE_SHARE_XML));
    		Method m = c.getMethod("indexPhrase", new Class[]{plasmaSwitchboard.class,String.class,String.class,String.class,byte[].class});
    		
    		// invoke the desired method
    		 m.invoke(null, new Object[] {this.switchboard,urlstring,phrase,(descr==null)?"":descr,md5});
    	} catch (Exception e) {
    		throw new AxisFault("Unable to index the file");
    	}    	
    }
    
    private void deletePhrase(String urlstring, String phrase, String descr) throws AxisFault {
    	try {
    		// getting the template class file
    		Class c = this.serverContext.getProvider().loadClass(this.serverContext.getServletClassFile(TEMPLATE_SHARE_XML));
    		Method m = c.getMethod("deletePhrase", new Class[]{plasmaSwitchboard.class,String.class,String.class,String.class});
    		
    		// invoke the desired method
    		 m.invoke(null, new Object[] {this.switchboard,urlstring,phrase,(descr==null)?"":descr});
    	} catch (Exception e) {
    		throw new AxisFault("Unable to index the file");
    	}      	
    }

    /**
     * Returns a directory listing in xml format
     * @param workingDirPath a relative path within the yacy file-share
     * @return the directory listing of the specified path as XML document
     * 
     * @throws Exception if the directory does not exist or can not be read
     */
    public Document getDirList(String workingDirPath) throws Exception {
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);   
				
		// getting the relativ path
		File htroot = getHtrootDir();
		File workingDir = getWorkingDir(workingDirPath);
		if (!workingDir.exists())      throw new AxisFault("Working directory does not exist.");
		if (!workingDir.canRead())     throw new AxisFault("Working directory is not readable.");
		if (!workingDir.isDirectory()) throw new AxisFault("Working directory is not a directory.");
		
		// generate the proper path for the servlet
		workingDirPath = workingDir.getCanonicalPath().substring(htroot.getCanonicalPath().length()+1);
		if (!workingDirPath.endsWith("/")) workingDirPath = workingDirPath + "/";
		
		// construct arguments
		this.requestHeader.put("PATH",workingDirPath);
    	
		// generating the template containing the network status information
		byte[] result = this.serverContext.writeTemplate(TEMPLATE_SHARE_XML, new serverObjects(), this.requestHeader);
		
		// sending back the result to the client
		return this.convertContentToXML(result);		
    }
    
    /**
     * Uploads a new file into the specified subdirectory of the yacy file-share directory.
     * The Uploaded file must be passed via SOAP Attachment
     * 
     * @param workingDirPath a relative path within the yacy file-share
     * @param indexFile specifies if the file should be indexed by yacy
     * @param comment a description of the file
     * 
     * @throws IOException
     * @throws SOAPException
     */
    public void uploadFile(String workingDirPath, boolean indexFile, String comment) throws IOException, SOAPException {
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);  
		
		// getting the full path
		File workingDir = getWorkingDir(workingDirPath);
		if (!workingDir.exists())      throw new AxisFault("Working directory does not exist.");		
		if (!workingDir.canWrite())    throw new AxisFault("Working directory is not writeable.");
		if (!workingDir.isDirectory()) throw new AxisFault("Working directory is not a directory.");
		
		// get the current message context
        MessageContext msgContext = MessageContext.getCurrentContext();

        // getting the request message
        Message reqMsg = msgContext.getRequestMessage();		
		
        // getting the attachment implementation
        Attachments messageAttachments = reqMsg.getAttachmentsImpl();
        if (messageAttachments == null) {
            throw new AxisFault("Attachments not supported");
        }		
        
        int attachmentCount= messageAttachments.getAttachmentCount();
        if (attachmentCount == 0) 
            throw new AxisFault("No attachment found");
        else if (attachmentCount != 1)
            throw new AxisFault("Too many attachments as expected.");     
        
        // getting the attachments
        AttachmentPart[] attachments = (AttachmentPart[])messageAttachments.getAttachments().toArray(new AttachmentPart[attachmentCount]);
        
        // getting the content of the attachment        
        DataHandler dh = attachments[0].getDataHandler();		
		String newFileName = attachments[0].getContentId();
		if (newFileName == null) newFileName = "newFile";
		
		// getting directory to create
		File newFile = getWorkingFile(workingDir,newFileName);
		if (newFile.exists())
			throw new AxisFault("File '" + newFileName + "' already exists");		    	
    	
		// copy datahandler content to file
		serverFileUtils.copy(dh.getInputStream(),newFile);

		// generate md5 sum and write it to file
		if (comment == null) comment = "";
		Object[] md5 = generateMD5File(newFile,comment);
        
        // index file
        if (indexFile) {
            String urlstring = yacyhURL(yacyCore.seedDB.mySeed, newFileName, (String)md5[GENMD5_MD5_STRING]);
            String phrase = newFileName.replace('.', ' ').replace('_', ' ').replace('-', ' ');
            indexPhrase(urlstring, phrase, comment, (byte[])md5[GENMD5_MD5_ARRAY]);        	
        }
    }
    
    /**
     * Creates a new directory 
     * @param workingDirPath a relative path within the yacy file-share
     * @param newDirName the name of the new directory
     * @throws IOException
     */
    public void createDirectory(String workingDirPath, String newDirName) throws IOException {
    	if (newDirName == null || newDirName.length() == 0) throw new AxisFault("The new directory name must not be null");
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);  
		
		// getting the full path
		File workingDir = getWorkingDir(workingDirPath);
		if (!workingDir.exists()) 	   throw new AxisFault("Working directory does not exist.");		
		if (!workingDir.canWrite())	   throw new AxisFault("Working directory is not writeable.");
		if (!workingDir.isDirectory()) throw new AxisFault("Working directory is not a directory");		
		
		// getting directory to create
		File newDirFile = getWorkingFile(workingDir,newDirName);
		if (newDirFile.exists())
			throw new AxisFault("Directory '" + newDirName + "' already exists");		
		
		// create Directory 
		newDirFile.mkdirs();
    }
    
    /**
     * Deletes a file or directory located in the yacy file-share directory
     * @param workingDirPath a relative path within the yacy file-share
     * @param nameToDelete the name of the file or directory that should be deleted.
     * <b>Attention:</b> Directories will be deleted recursively
     * 
     * @throws IOException
     */
    public void delete(String workingDirPath, String nameToDelete) throws IOException {
    	if (nameToDelete == null || nameToDelete.length() == 0) throw new AxisFault("The file name must not be null");
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);  
		
		// getting the full path
		File workingDir = getWorkingDir(workingDirPath);
		if (!workingDir.exists())      throw new AxisFault("Working directory does not exist.");		
		if (!workingDir.canWrite())    throw new AxisFault("Working directory is not writeable.");
		if (!workingDir.isDirectory()) throw new AxisFault("Working directory is not a directory");	
		
		// getting directory or file to delete
		File fileToDelete = getWorkingFile(workingDir, nameToDelete);		
		
		// delete file/dir
		this.deleteRecursive(fileToDelete);
    }
    
    /**
     * To download a file located in the yacy file-share.
     * This function returns the requested file as soap attachment to the caller of this function.
     * 
     * @param workingDirPath a relative path within the yacy file-share
     * @param fileName the name of the file that should be downloaded
     * @return the md5 sum of the downloaded file
     * 
     * @throws IOException
     * @throws SOAPException
     */
    public String getFile(String workingDirPath, String fileName) throws IOException, SOAPException {
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);  
		
		// getting the full path
		File workingDir = getWorkingDir(workingDirPath);
		if (!workingDir.exists())  	   throw new AxisFault("Working directory does not exist.");				
		if (!workingDir.canRead()) 	   throw new AxisFault("Working directory is not readable.");
		if (!workingDir.isDirectory()) throw new AxisFault("Working directory is not a directory");
		
		File workingFile = getWorkingFile(workingDir,fileName);
		if (!workingFile.exists()) throw new AxisFault("Requested file does not exist.");		
		if (!workingFile.canRead())throw new AxisFault("Requested file can not be read.");
		if (!workingFile.isFile()) throw new AxisFault("Requested file is not a file.");				
		
		String[] info = getFileInfo(workingFile);
		
		// get the current message context
        MessageContext msgContext = MessageContext.getCurrentContext();

        // getting the response message
        Message respMsg = msgContext.getResponseMessage();
		
        // creating a datasource and data handler
        DataSource data = new FileDataSource(workingFile);
        DataHandler attachmentFile = new DataHandler(data);
        
        AttachmentPart attachmentPart = respMsg.createAttachmentPart();
        attachmentPart.setDataHandler(attachmentFile);
        attachmentPart.setContentId(workingFile.getName());
        
        respMsg.addAttachmentPart(attachmentPart);
        respMsg.saveChanges();
        
        // return the md5 hash of the file as result
        return info[FILEINFO_MD5_STRING];
    }
    
    /**
     * To change the comment of a file located in the yacy file-share
     * @param workingDirPatha relative path within the yacy file-share
     * @param fileName the name of the file
     * @param comment the new comment
     * @param indexFile specifies if the file should be indexed by yacy
     * 
     * @throws IOException
     */
    public void changeComment(String workingDirPath, String fileName, String comment, boolean indexFile) throws IOException {
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);      	
    	
		// getting the full path
		File workingDir = getWorkingDir(workingDirPath);	
		if (!workingDir.exists())  	   throw new AxisFault("Working directory does not exist.");				
		if (!workingDir.canWrite())    throw new AxisFault("Working directory is not writeable.");
		if (!workingDir.isDirectory()) throw new AxisFault("Working directory is not a directory");	
		
		// getting wroking file
		File workingFile = getWorkingFile(workingDir,fileName);
		if (!workingFile.exists()) throw new AxisFault("Requested file does not exist.");		
		if (!workingFile.canRead())throw new AxisFault("Requested file can not be read.");
		if (!workingFile.isFile()) throw new AxisFault("Requested file is not a file.");	
		
		// getting filename and indexing phrase
		String filename = workingFile.getName();
		String phrase = filename.replace('.', ' ').replace('_', ' ').replace('-', ' ');
		
		// getting file info [0=md5s,1=comment]
		String[] info = getFileInfo(workingFile);
		
		// delete old indexed phrases
        String urlstring = yacyhURL(yacyCore.seedDB.mySeed, filename, info[FILEINFO_MD5_STRING]);        
        deletePhrase(urlstring, phrase, info[FILEINFO_COMMENT]);    					
		
		// re-generate md5 file [0=byteArray, 1=String]
		Object[] md5 = generateMD5File(workingFile,comment);

        // index file
        if (indexFile) {
            urlstring = yacyhURL(yacyCore.seedDB.mySeed, filename, (String)md5[GENMD5_MD5_STRING]);
            indexPhrase(urlstring, phrase, comment, (byte[])md5[GENMD5_MD5_ARRAY]);        	
        }		
    }
    
    private void deleteRecursive(File file) throws IOException {
    	if (file == null) throw new NullPointerException("File object is null");
    	if (!file.exists()) return;
    	if (!file.canWrite()) throw new IllegalArgumentException("File object can not be deleted. No write access.");
    	
    	if (file.isDirectory()) {    		
    		// delete all subdirectories and files
            File[] subFiles = file.listFiles();
            for (int i = 0; i < subFiles.length; i++) deleteRecursive(subFiles[i]);
    	} else {
    		// getting the file short name
    		String filename = file.getName();
    		int pos = file.getName().lastIndexOf("/");
    		if (pos != -1) filename = filename.substring(pos + 1);
    		
    		// getting file info [0=md5s,1=comment]
    		String[] info = getFileInfo(file);
    		
    		// delete indexed phrases
            String urlstring = yacyhURL(yacyCore.seedDB.mySeed, filename, info[FILEINFO_MD5_STRING]);
            String phrase = filename.replace('.', ' ').replace('_', ' ').replace('-', ' ');
            deletePhrase(urlstring, phrase, info[FILEINFO_MD5_STRING]);    		
    		
            // delete md5 file
            File md5File = getFileMD5File(file);
            if (md5File.exists()) md5File.delete();
    	}
    	
		// delete file / directory
		file.delete();    		
    }
}
