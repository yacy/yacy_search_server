/**
 *  SkillsConfig_p
 *  Copyright 2026 by Michael Peter Christen
 *  First released 08.02.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.htroot;

import java.util.List;

import net.yacy.ai.ToolProvider;
import net.yacy.ai.ToolProvider.ToolConfig;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class SkillsConfig_p {

    private static final String CONFIG_PREFIX = "ai.tools.";
    private static final String DESCRIPTION_SUFFIX = ".description";
    private static final String MAX_CALLS_SUFFIX = ".maxCallsPerTurn";

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        sb.setConfig("ui.SkillsConfig_p.visited", "true");

        int invalidMaxCalls = 0;
        if (post != null && post.containsKey("save")) {
            for (final ToolConfig tool : ToolProvider.listTools()) {
                final String descKey = CONFIG_PREFIX + tool.name + DESCRIPTION_SUFFIX;
                final String maxKey = CONFIG_PREFIX + tool.name + MAX_CALLS_SUFFIX;
                final String configuredDescription = post.get(descKey, tool.description);
                int configuredMaxCalls = tool.maxCallsPerTurn;
                final String maxCallsString = post.get(maxKey, Integer.toString(tool.maxCallsPerTurn)).trim();
                try {
                    configuredMaxCalls = Integer.parseInt(maxCallsString);
                } catch (final NumberFormatException e) {
                    invalidMaxCalls++;
                }
                if (configuredMaxCalls < 0) configuredMaxCalls = 0;
                sb.setConfig(descKey, configuredDescription);
                sb.setConfig(maxKey, Integer.toString(configuredMaxCalls));
            }
            prop.put("status", "1");
        } else {
            prop.put("status", "0");
        }

        prop.putNum("status_invalidMaxCalls", invalidMaxCalls);
        prop.put("status_hasInvalidMaxCalls", invalidMaxCalls > 0 ? "1" : "0");

        putToolGroup(prop, "basic_tools", ToolProvider.listBasicTools());
        putToolGroup(prop, "visualization_tools", ToolProvider.listVisualizationTools());
        putToolGroup(prop, "retrieval_tools", ToolProvider.listRetrievalTools());

        return prop;
    }

    /*
     * Naming note: internally these are called "tools", but in the UI we call
     * them "skills". Both terms refer to the same feature set.
     */
    private static void putToolGroup(final serverObjects prop, final String keyPrefix, final List<ToolConfig> tools) {
        int row = 0;
        for (final ToolConfig tool : tools) {
            prop.putHTML(keyPrefix + "_" + row + "_name", tool.name);
            prop.putHTML(keyPrefix + "_" + row + "_description", tool.description);
            prop.putNum(keyPrefix + "_" + row + "_maxCallsPerTurn", tool.maxCallsPerTurn);
            row++;
        }
        prop.put(keyPrefix, row);
    }
}
