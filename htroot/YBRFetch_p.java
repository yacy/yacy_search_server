import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.graphics.WebStructureGraph.HostReference;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.MetadataRepository;
import net.yacy.search.index.MetadataRepository.HostStat;
import net.yacy.search.index.Segment;
import net.yacy.search.ranking.BlockRank;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.servletProperties;

public class YBRFetch_p
{

    public static servletProperties respond(
        final RequestHeader requestHeader,
        final serverObjects post,
        final serverSwitch env) {
        final servletProperties prop = new servletProperties();
        final Switchboard sb = (Switchboard) env;

        if ( post == null || !post.containsKey("ghrt4") || MemoryControl.available() < 1024 * 1024 * 1024 ) {
            return prop;
        }
        final File hostIndexFile = new File(sb.queuesRoot, "hostIndex.blob");

        ReferenceContainerCache<HostReference> hostIndex; // this will get large, more than 0.5 million entries by now
        if ( !hostIndexFile.exists() ) {
            hostIndex = BlockRank.collect(sb.peers, sb.webStructure, Integer.MAX_VALUE);
            BlockRank.saveHostIndex(hostIndex, hostIndexFile);
        } else {
            hostIndex = BlockRank.loadHostIndex(hostIndexFile);
        }

        // use an index segment to find hosts for given host hashes
        final String segmentName = sb.getConfig(SwitchboardConstants.SEGMENT_PUBLIC, "default");
        final Segment segment = sb.indexSegments.segment(segmentName);
        final MetadataRepository metadata = segment.urlMetadata();
        Map<String, HostStat> hostHashResolver;
        try {
            hostHashResolver = metadata.domainHashResolver(metadata.domainSampleCollector());
        } catch ( final IOException e ) {
            hostHashResolver = new HashMap<String, HostStat>();
        }

        // recursively compute a new ranking table
        Log.logInfo("BLOCK RANK", "computing new ranking tables...");
        BlockRank.ybrTables = BlockRank.evaluate(hostIndex, hostHashResolver, null, 0);
        hostIndex = null; // we don't need that here any more, so free the memory

        // use the web structure and the hostHash resolver to analyse the ranking table
        Log.logInfo("BLOCK RANK", "analysis of " + BlockRank.ybrTables.length + " tables...");
        BlockRank.analyse(sb.webStructure, hostHashResolver);
        // store the new table
        Log.logInfo("BLOCK RANK", "storing fresh table...");
        final File rankingPath = new File(sb.appPath, "ranking/YBR".replace('/', File.separatorChar));
        BlockRank.storeBlockRankTable(rankingPath);
        BlockRank.loadBlockRankTable(rankingPath, 16);

        return prop;
    }

}
