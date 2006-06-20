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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class gettext{
    public static ArrayList createGettextRecursive(File sourceDir, String extensions, String notdir, File oldgettextfile) throws FileNotFoundException{
        if(oldgettextfile==null)
            return createGettextRecursive(sourceDir, extensions, notdir, (Map)null); //no old file
        return createGettextRecursive(sourceDir, extensions, notdir, parseGettext(oldgettextfile));
    }
    public static ArrayList createGettextRecursive(File sourceDir, String extensions, String notdir, Map oldgettext){
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
        list=createGettext(filenames, oldgettext);
        return list;
    }
    private static ArrayList getGettextHeader(){
        return getGettextHeader("UNKNOWN", "EMAIL");
    }
    private static ArrayList getGettextHeader(String translator, String email){
        ArrayList list=new ArrayList();
        list.add("#yacy translation");
        list.add("msgid \"\"");
        list.add("msgstr \"\"");
        list.add("\"Content-Type: text/plain; charset=UTF-8\\n\"");
        list.add("\"Content-Transfer-Encoding: 8bit\\n\"");
        
        list.add("\"Last-Translator: "+translator+"\\n\"");
        SimpleDateFormat dateformat=new SimpleDateFormat("yyyy-mm-dd HH:MMZ");
        list.add("\"PO-Revision-Date: "+dateformat.format(new Date())+"\\n\"");
        list.add("\"Project-Id-Version: YaCy\\n\"");
        
        list.add("\"Language-Team: <"+email+">\\n\"");
        list.add("\"X-Generator: YaCy\\n\"");
        
        list.add("\"Mime-Version: 1.0\\n\"");
        list.add("");
        return list;
    }
    public static ArrayList createGettext(ArrayList filenames, File oldgettextfile) throws FileNotFoundException{
        return createGettext(filenames, parseGettext(oldgettextfile));
    }
    /*
     * create a list of gettext file for some textfiles
     * @param filenames the ArrayList with the Filenames
     * @param oldgettextmap a map with the old translations.
     */
    public static ArrayList createGettext(ArrayList filenames, Map oldgettext){
        ArrayList list=new ArrayList();
        ArrayList tmp=null;
        String filename=null;
        Iterator it=filenames.iterator();
        list.addAll(getGettextHeader());
        
        while(it.hasNext()){
            try {
                filename=(String)it.next();
                tmp=getGettextSource(new File(filename), oldgettext);
            } catch (FileNotFoundException e) {
                System.out.println("File \""+filename+"\" not found.");
            }
            if(tmp!=null)
                list.addAll(tmp);
        }
        return list;
    }
    public static ArrayList getGettextSource(File inputfile, File oldmapfile) throws FileNotFoundException{
        if(oldmapfile != null && oldmapfile.exists())
            return getGettextSource(inputfile, parseGettext(oldmapfile));
        return getGettextSource(inputfile);
    }
    public static ArrayList getGettextSource(File inputfile) throws FileNotFoundException{
        return getGettextSource(inputfile, new HashMap());
    }
    public static ArrayList getGettextSource(File inputfile, Map oldgettextmap) throws FileNotFoundException{
        if(oldgettextmap==null)
            oldgettextmap=new HashMap();
        
        ArrayList strings=getGettextItems(inputfile);
        ArrayList list=new ArrayList();
        Iterator it=strings.iterator();
        if(strings.isEmpty())
            return null;
        list.add("#"+inputfile.getName());
        String key;
        while(it.hasNext()){
            key=((String)it.next()).replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n");
            list.add("msgid \""+key+"\"");
            if(oldgettextmap.containsKey(key))
                list.add("msgstr \""+oldgettextmap.get(key)+"\"");
            else
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
    public static HashMap parseGettext(File gettextfile) throws FileNotFoundException{
        ArrayList gettext=new ArrayList();
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(gettextfile)));
        try {
            line = br.readLine();
            while (line != null) {
                gettext.add(line);
                line = br.readLine();
            }
        } catch (IOException e) {}
        return parseGettext(gettext);
    }
    public static HashMap parseGettext(ArrayList gettext){
        HashMap map = new HashMap();
        int mode=0; //1= in msgid, 2= in msgstr
        String msgid = "", msgstr = "", tmp = "";
        
        Iterator it=gettext.iterator();
        while(it.hasNext()){
            tmp=(String) it.next();
            if(tmp.startsWith("msgid \"")){
                if(mode==2)
                    map.put(msgid, msgstr);
                msgid=tmp.substring(7,tmp.length()-1).replaceAll("\\\"", "\"");
                msgstr="";
                mode=1;
            }else if(tmp.startsWith("msgstr \"")){
                mode=2;
                msgstr=tmp.substring(8,tmp.length()-1);
            }else if(tmp.startsWith("\"")){
                //multiline strings with "..." on each line
                if(mode==1){
                    msgid+="\n"+tmp.substring(1,tmp.length()-1).replaceAll("\\\"", "\"");
                }else if(mode==2){
                    msgstr+="\n"+tmp.substring(1,tmp.length()-1).replaceAll("\\\"", "\"");
                }
            }
        }
        map.put(msgid, msgstr); //the last one cannot be put, on the next msgid ;-)
        return map;
    }
    public static void main(String[] argv){
        if(argv.length < 2){
            System.out.println("Syntax: java de.anomic.data.gettext creategettext [inputfile] ... [inputfile]");
            System.out.println("Syntax: java de.anomic.data.gettext parsegettext [gettextfile]");
            System.out.println("Syntax: java de.anomic.data.gettext updategettext [gettextfile] [inputfile] ... [inputfile]");
            System.exit(1);
        }
        if(argv[0].equals("creategettext")){
            ArrayList filelist=new ArrayList();
            for(int i=1;i<argv.length;i++){
                filelist.add(argv[i]);
            }
            ArrayList list = createGettext(filelist, (Map)null);
            Iterator it=list.iterator();
            while(it.hasNext())
                System.out.println((String)it.next());
        }else if(argv[0].equals("parsegettext")){
            if(argv.length >2){
                System.out.println("only one file allowed for parsegettext");
                System.exit(1);
            }
            try {
                HashMap translations=parseGettext(new File(argv[1]));
                Iterator it=translations.keySet().iterator();
                String key="";
                while(it.hasNext()){
                    key=(String)it.next();
                    System.out.println("key: "+key);
                    System.out.println("value: "+translations.get(key));
                }
            } catch (FileNotFoundException e) {
                System.exit(1);
            }
        }else if(argv[0].equals("updategettext")){
            if(argv.length < 3){
                System.out.println("Too less arguments");
                System.exit(1);
            }
            ArrayList filelist=new ArrayList();
            for(int i=2;i<argv.length;i++){
                filelist.add(argv[i]);
            }
            try {
                ArrayList list=createGettext(filelist, new File(argv[1]));
                Iterator it=list.iterator();
                while(it.hasNext())
                    System.out.println((String)it.next());
            } catch (FileNotFoundException e) {
                System.out.println("File not found.");
                System.exit(1);
            }
        }else{
            System.out.println("unknown Mode ...");
            System.exit(1);
        }
    }
}