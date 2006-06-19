//gettext.java - translations in a simplified gettext-format
//----------------------------------------------------------
//part of YaCy
//
// (C) 2006 by Alexander Schier
//
// last change: $LastChangedDate:  $ by $LastChangedBy: $
// $LastChangedRevision: $
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

package de.anomic.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import de.anomic.server.serverSwitch;

public class gettext{
    public static String createGettextOutput(serverSwitch env){
        return "";
    }
    public static ArrayList createGettextRecursive(File sourceDir, String extensions, String notdir){
        ArrayList list=new ArrayList();
        ArrayList exts=listManager.string2arraylist(extensions);
        Iterator it2;
        String filename;
        ArrayList filenames=new ArrayList();
     
        ArrayList dirList=listManager.getDirsRecursive(sourceDir, notdir);
        dirList.add(sourceDir);
        Iterator it=dirList.iterator();
        File dir=null;
        File[] files;
        //this looks a lot more complicated, than it is ...
        while(it.hasNext()){
            dir=(File)it.next();
            if(dir.isDirectory() && !dir.getName().equals(notdir)){
                files=dir.listFiles();
                for(int i=0;i<files.length;i++){
                    if(!files[i].isDirectory()){
                        it2=exts.iterator();
                        filename=files[i].getName();
                        while(it2.hasNext()){
                            if(filename.endsWith((String)it2.next())){
                                try {
                                    filenames.add(files[i].getCanonicalPath());
                                } catch (IOException e) {
                                    // TODO Auto-generated catch block
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        list=createGettext(filenames);
        return list;
    }
    /*
     * create a list of gettext file for some textfiles
     * @param filenames the ArrayList with the Filenames
     */
    public static ArrayList createGettext(ArrayList filenames){
        ArrayList list=new ArrayList();
        ArrayList tmp=null;
        String filename=null;
        Iterator it=filenames.iterator();
        
        list.add("#yacy translation");
        list.add("msgid \"\"");
        list.add("msgstr \"\"");
        list.add("\"Content-Type: text/plain; charset=UTF-8\\n\"");
        list.add("\"Content-Transfer-Encoding: 8bit\\n\"");
        
        list.add("\"Last-Translator: UNKNOWN\\n\"");
        SimpleDateFormat dateformat=new SimpleDateFormat("yyyy-mm-dd HH:MMZ");
        list.add("\"PO-Revision-Date: "+dateformat.format(new Date())+"\\n\"");
        list.add("\"Project-Id-Version: YaCy\\n\"");
        
        list.add("\"Language-Team: <EMAIL>\"");
        list.add("\"X-Generator: YaCy\\n\"");
        
        list.add("\"Mime-Version: 1.0\\n\"");
        list.add("");
        
        while(it.hasNext()){
            try {
                filename=(String)it.next();
                tmp=getGettextSource(new File(filename));
            } catch (FileNotFoundException e) {
                System.out.println("File \""+filename+"\" not found.");
            }
            if(tmp!=null)
                list.addAll(tmp);
        }
        return list;
    }
    public static ArrayList getGettextSource(File inputfile) throws FileNotFoundException{
        ArrayList strings=getGettextItems(inputfile);
        ArrayList list=new ArrayList();
        Iterator it=strings.iterator();
        list.add("#"+inputfile.getName());
        while(it.hasNext()){
            list.add("msgid \""+((String)it.next()).replaceAll("\"", "\\\"").replaceAll("\n", "\\\\n")+"\"");
            list.add("msgstr \"\"");
            list.add("");
        }
        return list;
    }
    /*
     * create a list of gettext Strings ( _() ) from a file
     * @param inputfile the file, which contains the raw Strings.
     */
    public static ArrayList getGettextItems(File inputfile) throws FileNotFoundException{
        ArrayList list=new ArrayList();
        int character;
        InputStreamReader reader;
        int state=0; //0=no gettext macro 1= _ found, 2= ( found and in the string 3=\ found
        String untranslatedString="";
        
        reader = new InputStreamReader(new FileInputStream(inputfile));
        try {
            character=reader.read();
            while(character >=0) {
                if(state==0 && (char)character=='_')
                    state=1;
                else if(state==1){
                    if((char)character=='('){
                        state=2;
                        untranslatedString="";
                    }else{
                        state=0;
                        untranslatedString+=(char)character;
                    }
                }else if(state==2){
                    if((char)character=='\\')
                        state=3;
                    else if((char)character==')'){
                        state=0;
                        list.add(untranslatedString);
                    }else{
                        untranslatedString+=(char)character;
                    }
                }else if(state==3){
                    state=2;
                    if((char)character==')')
                        untranslatedString+=")";
                    else
                        untranslatedString+="\\"+(char)character;
                }else{
                    untranslatedString+=(char)character;
                }
                character=reader.read();
            }
        } catch (IOException e) {}
        return list;
    }
    public static void main(String[] argv){
        if(argv.length < 1){
            System.out.println("Syntax: java de.anomic.data.gettext [inputfile]");
            System.exit(1);
        }
        ArrayList filelist=new ArrayList();
        for(int i=0;i<argv.length;i++){
            filelist.add(argv[i]);
        }
        ArrayList list = createGettext(filelist);
        Iterator it=list.iterator();
        while(it.hasNext())
            System.out.println((String)it.next());
    }
}