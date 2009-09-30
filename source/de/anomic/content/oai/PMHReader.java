// PMHReader
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.09.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-09-23 23:26:14 +0200 (Mi, 23 Sep 2009) $
// $LastChangedRevision: 6340 $
// $LastChangedBy: low012 $
//
// LICENSE
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

package de.anomic.content.oai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;

import de.anomic.content.DCEntry;
import de.anomic.content.file.SurrogateReader;
import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.crawler.retrieval.LoaderDispatcher;
import de.anomic.crawler.retrieval.Request;
import de.anomic.crawler.retrieval.Response;
import de.anomic.yacy.yacyURL;

public class PMHReader {

    LoaderDispatcher loader;
    
    public PMHReader(LoaderDispatcher loader) {
        this.loader = loader;
    }
    
    public void load(yacyURL source) throws IOException {
        Response response = this.loader.load(source, true, true, CrawlProfile.CACHE_STRATEGY_NOCACHE);
        load(response);
    }
    
    public static void load0(yacyURL source) throws IOException {
        Response response = HTTPLoader.load(new Request(source, null));
        load(response);
    }
    
    private static void load(Response response) throws IOException {
        byte[] b = response.getContent();
        SurrogateReader sr = new SurrogateReader(new ByteArrayInputStream(b), 100);
        Thread srt = new Thread(sr);
        srt.start();
        DCEntry dce;
        while ((dce = sr.take()) != DCEntry.poison) {
            System.out.println(dce.toString());
        }
        try {
            srt.join();
        } catch (InterruptedException e) {}
    }
    
    public static void main(String[] args) {
        // get one server with
        // http://roar.eprints.org/index.php?action=csv
        // list records from oai-pmh like
        // http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListRecords&metadataPrefix=oai_dc
        try {
            load0(new yacyURL(args[0], null));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
