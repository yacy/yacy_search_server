//
//  YaCyErrorHandler
//  ----------------
//  Copyright 2014 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
//  First released 2014 at http://yacy.net
//
//  $LastChangedDate$
//  $LastChangedRevision$
//  $LastChangedBy$
//
//  This library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 2.1 of the License, or (at your option) any later version.
//
//  This library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//  Lesser General Public License for more details.
//
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program in the file lgpl21.txt
//  If not, see <http://www.gnu.org/licenses/>.
//

package net.yacy.http;

import java.io.IOException;
import java.io.Writer;
import javax.servlet.http.HttpServletRequest;
import net.yacy.peers.operation.yacyBuildProperties;
import org.eclipse.jetty.server.handler.ErrorHandler;

/**
 * Custom Handler to serve error pages called by the HttpResponse.sendError method
 */
public class YaCyErrorHandler extends ErrorHandler {

    @Override
    protected void writeErrorPageBody(HttpServletRequest request, Writer writer, int code, String message, boolean showStacks)
            throws IOException {
        String uri = request.getRequestURI();

        writeErrorPageMessage(request, writer, code, message, uri);
        if (showStacks) {
            writeErrorPageStacks(request, writer);
        }
        writer.write("<br/><hr /><small>YaCy " + yacyBuildProperties.getVersion() + "  - <i> powered by Jetty </i> - </small>");
        for (int i = 0; i < 20; i++) {
            writer.write("<br/>                                \n");
        }
    }
}
