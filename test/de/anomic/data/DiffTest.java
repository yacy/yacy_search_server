package de.anomic.data;

import junit.framework.TestCase;

public class DiffTest extends TestCase {   
    final static boolean  _1 = true;            // temporary variables to make the matrix more readable
    final static boolean  __ = false;
    
 boolean[][] matrix =
 {
// 0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 
 {__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,_1,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,_1,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,_1,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,_1,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{_1,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,_1,__,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,_1,__,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,_1,__,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,_1,__,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,_1,__,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
,{__,__,__,__,__,__,_1,__,__,__,__,__,__,__,__,__,__,_1,_1,_1}
 };
/*
 boolean[][] matrix =       //  [x][y]     // a another test matrix
 {
// 0  1  2  3  4  5  // X == 6
 {__,__,__,__,__,__} // 0
,{_1,__,__,__,__,__} // 1
,{__,_1,__,__,__,__} // 2
,{__,__,_1,__,__,__} // 3
,{__,__,__,_1,__,__} // 4
,{__,__,__,__,_1,__} // 5   
,{__,__,__,__,__,_1} // 6
 };                  // Y == 7
*/
    /** the old buggy version */
    private static int[] findDiagonalOld(int x, int y, boolean[][] matrix, int minLength) {
         int rx, ry, yy, xx, i;
         // Zeilenweise nach Diagonalen mit mindest-Laenge minLength suchen
         for (yy=y; yy<matrix.length; yy++)
             for (xx=x; xx<matrix[yy].length; xx++)
                 if (matrix[yy][xx]) {
                     rx = xx;
                     ry = yy;
                     for (i=1; (yy + i)<matrix.length && (xx + i)<matrix[yy].length; i++)
                         if (!matrix[yy + i][xx + i]) break;
                     if (i <= minLength && yy + i < matrix.length && xx + i < matrix[yy].length) {
                         // vorzeitig abgebrochen => zuwenige chars in Diagonale => weitersuchen
                         continue;
                     } else {
                         return new int[] { rx, ry, i };
                     }
                 }
         return null;
    }

    /** the fixed version */
    private static int[] findDiagonalNew(int x, int y, boolean[][] matrix, int minLength) {
         int rx, ry, yy, xx, i;
         for (yy=y; yy<matrix.length; yy++)
             for (xx=x; xx<matrix[yy].length; xx++)
                 if (matrix[yy][xx]) {       // reverse order! [y][x]
                     rx = xx;
                     ry = yy;
                     for (i=1; (yy + i)<matrix.length && (xx + i)<matrix[yy].length; i++)
                         if (!matrix[yy + i][xx + i]) 
                             break;
                     if (i >= minLength)
                         return new int[] { rx, ry, i };     // swap back the x and y axes for better readability 
                 }
         return null;
    }
    
    public void testReplace() 
    {
        int[] vectorOld;
        int[] vectorNew;
        int minLength = 6;
        int x = 0;
        int y = 0;
        
        System.out.println("matrix.length: " + matrix.length) ;
        System.out.println("matrix[0].length: " + matrix[0].length) ;
        
        vectorOld = findDiagonalOld(x, y, matrix, minLength);
        vectorNew = findDiagonalNew(x, y, matrix, minLength);
        
        System.out.print("vectorOld: [ ");  
        if (vectorOld != null)
            for (int i = 0; i < vectorOld.length; i++) 
                System.out.print(vectorOld[i] + ", ");
        System.out.println("]");
        
        System.out.print("vectorNew: [ ");  
        if (vectorNew != null)
            for (int i = 0; i < vectorNew.length; i++) 
                System.out.print(vectorNew[i] + ", ");      
        System.out.println("]");
    }
}