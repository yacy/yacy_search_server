//plasmaSearchResults.java - a container for searchresults.
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

package de.anomic.data;
import java.util.ArrayList;
import java.util.Date;

import de.anomic.index.indexURLEntry;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSnippetCache;

public class searchResults {
    private int totalcount=0;
    private int filteredcount=0;
    private int orderedcount=0;
    private int linkcount=0;
    private int globalresults=0;
    private plasmaSearchRankingProfile ranking=null;
    private String formerSearch="";
    private plasmaSearchQuery query=null;
    private ArrayList results=null;
    private Object[] references=null;
    
    public searchResults(){
        this.results=new ArrayList();
    }
    public searchResults(int totalcount, int filteredcount, int orderedcount, int linkcount){
        this.results=new ArrayList();
        this.totalcount=totalcount;
        this.filteredcount=filteredcount;
        this.orderedcount=orderedcount;
        this.linkcount=linkcount;
    }
    public void appendResult(searchResult result){
        if (results==null)
            results=new ArrayList();
        results.add(result);
    }
    public int numResults(){
        if(results==null) return 0;
        return results.size();
    }
    public searchResult getResult(int index){
        if(results==null || results.size()-1<index)
            return null;
        return (searchResult)results.get(index);
    }
    public void setTotalcount(int totalcount) {
        this.totalcount = totalcount;
    }
    public int getTotalcount() {
        return totalcount;
    }
    public void setFilteredcount(int filteredcount) {
        this.filteredcount = filteredcount;
    }
    public int getFilteredcount() {
        return filteredcount;
    }
    public void setOrderedcount(int orderedcount) {
        this.orderedcount = orderedcount;
    }
    public int getOrderedcount() {
        return orderedcount;
    }
    public void setLinkcount(int linkcount) {
        this.linkcount = linkcount;
    }
    public int getLinkcount() {
        return linkcount;
    }
    public void setGlobalresults(int globalresults) {
        this.globalresults = globalresults;
    }
    public int getGlobalresults() {
        return globalresults;
    }
    public void setRanking(plasmaSearchRankingProfile ranking) {
        this.ranking = ranking;
    }
    public plasmaSearchRankingProfile getRanking() {
        return ranking;
    }
    public searchResult createSearchResult(){
        return new searchResult();
    }
    public void setFormerSearch(String formerSearch) {
        this.formerSearch = formerSearch;
    }
    public String getFormerSearch() {
        return formerSearch;
    }
    public void setQuery(plasmaSearchQuery query) {
        this.query = query;
    }
    public plasmaSearchQuery getQuery() {
        return query;
    }
    public void setReferences(Object[] references) {
        this.references = references;
    }
    public Object[] getReferences() {
        return references;
    }
    public class searchResult{
        private String url="";
        private String urlname="";
        private plasmaSnippetCache.TextSnippet snippet=null;
        private indexURLEntry urlentry=null;
        
        public searchResult(){
            
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        public String getUrl() {
            return url;
        }
        public void setUrlname(String urlname) {
            this.urlname = urlname;
        }
        public String getUrlname() {
            return urlname;
        }
        public void setSnippet(plasmaSnippetCache.TextSnippet snippet) {
            this.snippet = snippet;
        }
        public plasmaSnippetCache.TextSnippet getSnippet() {
            return snippet;
        }
        public void setUrlentry(indexURLEntry urlentry) {
            this.urlentry = urlentry;
        }
        public indexURLEntry getUrlentry() {
            return urlentry;
        }
        public String getUrlhash(){
            return urlentry.hash();
        }
        public boolean hasSnippet(){
            return this.snippet!=null && this.snippet.exists();
        }
    }
}
