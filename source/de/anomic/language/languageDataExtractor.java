// languageDataExtractor.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Marc Nause
//
// $LastChangedDate: 2007-07-29 $
// $LastChangedRevision: $
// $LastChangedBy: low012 $
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



// This program creates a file with information about the percentage
// in which letters are included in a text.
//
// The program can be started with the following arguments:
//
// input=filename     name of the file the text is stored in
// output=filename    name of the file the data will be stored in
// name=langugaename  name of the language the text is written in
// name=code          code of the language the text is written in (e.g. en-GB)

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

public class languageDataExtractor {

    public static void main (String[] args) {

        BufferedReader inputFile = null;
        BufferedWriter outputFile = null;
        char key = 'x';
        Float fValue = new Float(0); 
        HashMap map = new HashMap();
        int quantity = 0;
        Iterator mapiter;
        String code = "";
        String file = "";
        String input = "";
        String line = "";
        String name = "";
        String output = "";
        String sKey = "";

        //Program started with arguments 'input', 'data', 'name'?
        if (args.length <= 4) {

            for(int i=0;i<args.length;i++){

                String temp = args[i];

                if (temp.startsWith("input=")) {
                    input = temp.substring(6);
                } else if (temp.startsWith("name=")) {
                    name = temp.substring(5);
                } else if (temp.startsWith("code=")) {
                    code = temp.substring(5);
                }
            }

        }

        //Ask user if arguments were not used when starting program.
        if ((input.equals("")) || (name.equals("")) || (code.equals(""))) {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            try {
                if(input.equals("")) {
                    System.out.print("Name of file text is to be read from: ");
                    input = in.readLine();
                }
                if(name.equals("")) {
                    System.out.print("Name of the language the text is written in: ");
                    name = in.readLine();
                }
                if(code.equals("")) {
                    System.out.print("Code of the language the text is writen in (e.g. en-GB): ");
                    code = in.readLine();
                }
            }
            catch (IOException e) {
                System.out.println(e);
                System.out.println("Program aborted!");
            }
        }

        //Creating filename from language code plus .ldf (language data file)
        output = code + ".ldf";

        //Trying to open input file.
        try {
            inputFile = new BufferedReader(new InputStreamReader(new FileInputStream(input), "UTF8"));
        }
        catch (IOException e) {
            System.out.println("Error openinging file "+input);
            System.out.println("Program aborted! No data has been written!");
            System.exit(1);
        }

        //Trying to read from input file and put quantity of letters into map.
        try {
            while ((line = inputFile.readLine()) != null) {
                for(int i=0;i<line.length();i++){
                    key = line.charAt(i);
                    if(Character.isLetter(key)){
                        key = Character.toLowerCase(key);
                        sKey = "" + key;
                        if (map.containsKey(sKey)) {
                            fValue = new Float(Float.parseFloat(map.get(sKey).toString()) + 1);
                        }
                        else {
                            fValue = new Float(1);
                        }
                        map.put(sKey, fValue);
                        quantity++;
                    }
                }
            }
        }
        catch (IOException e) {
            System.out.println("Error reading file "+input);
            System.out.println("Program aborted! No data has been written!");
            System.exit(1);
        }

        //Creating content for file.
        file = "<language name=\""+name+"\" code=\""+code+"\">\n";
        mapiter = map.keySet().iterator();
        while(mapiter.hasNext()){
            key = mapiter.next().toString().charAt(0);
            sKey = "" + key; 
            file += "\n  <letter>\n    <name>"+key+"</name>\n    <quantity>"+(Float.parseFloat(map.get(sKey).toString())/quantity*100)+"</quantity>\n  </letter>\n";
        }
        file += "\n</language>";

        //Writing file.
        try {
            outputFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF8"));
            outputFile.write(file);
            outputFile.close();
        }
        catch (IOException e) {
            System.out.println("Error writing file "+input);
            System.out.println("Program aborted! No data has been written!");
            System.exit(1);
        }
    }
}
