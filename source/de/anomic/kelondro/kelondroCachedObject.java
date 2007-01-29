//kelondroCachedObject - Cache Object for kelondroCachedObjectMap
//----------------------------------------------------------
//part of YaCy
//
// (C) 2007 by Alexander Schier
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

package de.anomic.kelondro;

import java.util.HashMap;
import java.util.Map;


    public class kelondroCachedObject{
        protected Map map;
        protected String key;
        public kelondroCachedObject(){
            this.map=new HashMap();
            this.key="";
        }
        public kelondroCachedObject(Map map){
            this.map=map;
            this.key="";
        }
        public kelondroCachedObject(String key, Map map){
            this.key=key;
            if(map!=null)
                this.map=map;
            else
                this.map=new HashMap();
        }
        public Map toMap(){
            return map;
        }
        public String getKey(){
            return key;
        }
    }

   
