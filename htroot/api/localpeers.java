// localpeers.java
// ------------
// (C) 2021 by Michael Peter Christen; mc@yacy.net
// first published 23.01.2021 on http://yacy.net
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

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.Memory;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.servletProperties;

// http://localhost:8090/api/localpeers.json
public class localpeers {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {
        final servletProperties prop = new servletProperties();
        int c = 0;
        for (final String urlstub: Switchboard.getSwitchboard().localcluster_scan) {
            prop.putJSON("peers_" + c + "_urlstub", urlstub); // a usrlstub is a full url with protocol, host and port up the the path start including first "/"
            c++;
        }
        prop.put("peers", c);
        try {
            prop.put("status", systemStatus().toString(2));
        } catch (final JSONException e) {
            prop.put("status", "");
        }
        // return rewrite properties
        return prop;
    }

    public static JSONObject systemStatus() throws JSONException {

        // generate json
        final JSONObject systemStatus = new JSONObject(true);
        Memory.status().forEach((k, v) -> {try {systemStatus.put(k, v);} catch (final JSONException e) {}});
        final JSONArray members = new JSONArray();
        final HazelcastInstance hi = Switchboard.getSwitchboard().localcluster_hazelcast;
        if (hi != null) {
	        String uuid = hi.getCluster().getLocalMember().getUuid().toString();
	        hi.getMap("status").put(uuid, Memory.status());
	        for (final Member member: hi.getCluster().getMembers()) {
	            final JSONObject m = new JSONObject(true);
	            uuid = member.getUuid().toString();
	            m.put("uuid", uuid);
	            m.put("host", member.getAddress().getHost());
	            try {m.put("ip", member.getAddress().getInetAddress().getHostAddress());} catch (JSONException | UnknownHostException e) {}
	            m.put("port", member.getAddress().getPort());
	            m.put("isLite", member.isLiteMember());
	            m.put("isLocal", member.localMember());
	            @SuppressWarnings("unchecked")
				final
	            Map<String, Object> status = (Map<String, Object>) hi.getMap("status").get(uuid);
	            m.put("status", status);
	            members.put(m);
	        }
	        systemStatus.put("hazelcast_cluster_name", hi.getConfig().getClusterName());
	        systemStatus.put("hazelcast_instance_name", hi.getConfig().getInstanceName());
	        final Collection<String> interfaces = hi.getConfig().getNetworkConfig().getInterfaces().getInterfaces();
	        systemStatus.put("hazelcast_interfaces", interfaces);
	        systemStatus.put("hazelcast_members", members);
	        systemStatus.put("hazelcast_members_count", members.length());
        }

        return systemStatus;
    }
}