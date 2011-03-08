// PartitionScheme.java 
// ------------------------------
// part of YaCy
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 28.01.2009
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.yacy.dht;

import de.anomic.yacy.yacySeed;

/**
 * A PartitionScheme is a calculation of index storage positions in the network of peers.
 * Depending on the size of the network and the requirements of use cases the network
 * may be defined in different ways. A search network can be constructed in two different ways:
 *   * partition by document
 *   * partition by word
 * Both schemes have different advances and disadvantages:
 * A partition by document has the following properties:
 *   + all documents are distinct from each other, no double occurrences of documents and document infos
 *   + good performance scaling up to about 100 peers
 *   + no index distribution necessary
 *   - not good scaling for a very large number of peers (1000+ servers cannot be requested simulanously)
 * A partition by word has the following properties:
 *   + can scale for a very large number of peers
 *   + can almost unlimited scale in number of documents because the number of peers may scale to really large numbers
 *   + in case of a search only a very small number of peers must be asked
 *   - double occurrences of documents cannot be avoided very easy
 *   - index distribution is necessary and is IO and bandwidth-intensive
 *   - a search request does not scale good, because so many references are accumulated at a single peer
 * The partition by word may be enhanced: a vertical scaling provides a better scaling for search,
 * but increases further the complexity of the distribution process.
 * 
 * In YaCy we implement a word partition with vertical scaling. To organize the complexity of the
 * index distribution and peers selection in case of a search request, this interface is needed to
 * implement different distribution schemes.
 * 
 * @author Michael Christen
 *
 */
public interface PartitionScheme {

    public int verticalPartitions();
    
    public long dhtPosition(final byte[] wordHash, final String urlHash);
    
    public long dhtPosition(final byte[] wordHash, final int verticalPosition);
    
    public int verticalPosition(final byte[] urlHash);

    public long[] dhtPositions(final byte[] wordHash);
 
    public long dhtDistance(final byte[] word, final String urlHash, final yacySeed peer);
    
    
}
