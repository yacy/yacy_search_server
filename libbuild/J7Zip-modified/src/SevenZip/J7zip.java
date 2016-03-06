package SevenZip;

import SevenZip.Archive.SevenZip.Handler;
import SevenZip.Archive.SevenZipEntry;
import SevenZip.Archive.IArchiveExtractCallback;
import SevenZip.Archive.IInArchive;

import java.io.IOException;
import java.text.DateFormat;

import java.util.Vector;

public class J7zip {
	
    static void PrintHelp() {
        System.out.println(
                "\nUsage:  JZip <l|t|x> <archive_name> [<file_names>...]\n" +
                "  l : Lists files\n" +
                "  t : Tests archive.7z\n" +
                "  x : eXtracts files\n");
    }
    
    static void listing(IInArchive archive,Vector listOfNames,boolean techMode) {
        
        if (!techMode) {
            System.out.println("  Date   Time   Attr         Size   Compressed  Name");
            System.out.println("-------------- ----- ------------ ------------  ------------");
        }
        
        long size = 0;
        long packSize = 0;
        long nbFiles = 0;
        
        for(int i = 0; i < archive.size() ; i++) {
            SevenZipEntry item = archive.getEntry(i);
            
            DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT , DateFormat.SHORT );
            String str_tm = formatter.format(new java.util.Date(item.getTime()));
            
            if (listOfNames.contains(item.getName())) {
                if (techMode) {
                    System.out.println("Path = " + item.getName());
                    System.out.println("Size = " + item.getSize());
                    System.out.println("Packed Size = " + item.getCompressedSize());
                    System.out.println("Modified = " + str_tm);
                    System.out.println("   Attributes : " + item.getAttributesString());
                    long crc = item.getCrc();
                    if (crc != -1)
                        System.out.println("CRC = " + Long.toHexString(crc).toUpperCase());
                    else
                        System.out.println("CRC =");
                    System.out.println("Method = " + item.getMethods() );
                    System.out.println("" );
                    
                } else {
                    System.out.print(str_tm + " " + item.getAttributesString());
                    
                    System.out.print(item.getSize());
                    
                    System.out.print(item.getCompressedSize());
                    
                    System.out.println("  " + item.getName());
                }
                
                size += item.getSize();
                packSize += item.getCompressedSize();
                nbFiles ++;
            }
        }
        
        if (!techMode) {
            System.out.println("-------------- ----- ------------ ------------  ------------");
            System.out.print("                    " + size + packSize + " " + nbFiles);
        }
    }
    
    static void testOrExtract(IInArchive archive, Vector listOfNames,int mode) throws Exception {
        
        ArchiveExtractCallback extractCallbackSpec = new ArchiveExtractCallback();
        IArchiveExtractCallback extractCallback = extractCallbackSpec;
        extractCallbackSpec.Init(archive);
        extractCallbackSpec.PasswordIsDefined = false;
        
        try {  
            int len = 0;
            int arrays []  = null;
            
            if (listOfNames.size() >= 1) {
                arrays = new int[listOfNames.size()];
                for(int i = 0 ; i < archive.size() ; i++) {
                    if (listOfNames.contains(archive.getEntry(i).getName())) {
                        arrays[len++] = i;
                    }
                }
            }
            
            if (len == 0) {
                archive.Extract(null, -1, mode , extractCallback);
            } else {
                archive.Extract(arrays, len, mode, extractCallback);
            }
            
            if (extractCallbackSpec.NumErrors == 0)
                System.out.println("Ok Done");
            else
                System.out.println(" " + extractCallbackSpec.NumErrors + " errors");
        } catch (IOException e) {
            System.out.println("IO error : " + e.getLocalizedMessage());
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("\nJ7zip 4.43 ALPHA 2 (" + Runtime.getRuntime().availableProcessors() + " CPUs)");
        
        if (args.length < 2) {
            PrintHelp();
            return ;
        }
        
        final int MODE_LISTING = 0;
        final int MODE_TESTING = 1;
        final int MODE_EXTRACT = 2;
        
        int mode = -1;
        
        Vector listOfNames = new Vector();
        for (int i = 2;i < args.length ; i++)
            listOfNames.add(args[i]);
        
        if (args[0].equals("l")) {
            mode = MODE_LISTING;
        } else if (args[0].equals("t")) {
            mode = MODE_TESTING;
        } else if (args[0].equals("x")) {
            mode = MODE_EXTRACT;
        } else {
            PrintHelp();
            return ;
        }
        
        String filename = args[1];
        
        MyRandomAccessFile istream = new MyRandomAccessFile(filename,"r");
        
        IInArchive archive = new Handler(istream);
        
        
        switch(mode) {
            case MODE_LISTING:
                listing(archive,listOfNames,false);
                break;
            case MODE_TESTING:
                testOrExtract(archive,listOfNames,IInArchive.NExtract_NAskMode_kTest);
                break;
            case MODE_EXTRACT:
                testOrExtract(archive,listOfNames,IInArchive.NExtract_NAskMode_kExtract);
                break;
        }
        
        archive.close();
    }
}
