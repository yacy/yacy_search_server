/**
 * ClickServlet 
 * Copyright 2014 by reger
 * First released 04.01.2015 at http://yacy.net
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt If not, see
 * <http://www.gnu.org/licenses/>.
 */

package net.yacy.http.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

/**
 * The ClickServlet is used as search result link to perform additional actions
 * upon click on the link by user. The actual target url is given as parameter,
 * the servlet forwards the user to the target link page and performs additonal
 * actions with the target url (basically alternative of using javascript
 * href.onClick() )
 *
 * Request Parameter: url= the target User browser is forwarded to the url using
 * html header or javascript afterwards performs configured actions,
 *
 * Actions e.g. (0- = not implemented yet)
 * - crawl/recrawl the url
 * - crawl all links on page (with depth) / site
 * 0- increase/create rating
 * 0- add to a collection
 * 0- connect query and url
 * 0- learn and classify content - promote rating
 * 0- add to click statistic url/cnt (maybe to use for boost)
 */
public class ClickServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // config switches to remember actions to perform
    String _actionCode = "index";

    static final String crawlaction = "crawl"; // actionCode to add url to crawler with crawldepth=0
    static final String indexaction = "index"; // actionCode to add url to index (=default)
    static final String crawllinksaction = "crawllinks"; // actionCode to add url to crawler with crawldepth=1

    @Override
    public void init() {
        if (this.getInitParameter("clickaction") != null) {
            _actionCode = this.getInitParameter("clickaction");
        }
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException {

        HttpServletRequest hrequest = (HttpServletRequest) request;
        HttpServletResponse hresponse = (HttpServletResponse) response;

        final String strUrl = hrequest.getParameter("url");
        if (strUrl == null) {
            hresponse.sendError(HttpServletResponse.SC_NOT_FOUND, "url parameter missing");
            return;
        }

        try {
            hresponse.setStatus(HttpServletResponse.SC_OK);
            /* alternative to use javascript / http-equiv header
             hresponse.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
             hresponse.setHeader(HeaderFramework.LOCATION, strUrl);
             */

            // output html forward to url header
            PrintWriter pw = response.getWriter();
            response.setContentType("text/html");

            pw.println("<html>");
            pw.println("<head>");

            pw.print("<script>window.location.replace(\"");
            pw.print(strUrl);
            pw.println("\");</script>");

            pw.print("<noscript><META http-equiv=\"refresh\" content=\"0; URL=");
            pw.print(strUrl);
            pw.println("\"></noscript>");

            pw.println("</head></html>");
            pw.close();

            if (Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.SEARCH_USECLICKSERVLET, false)) {

                // do click event action
                if (_actionCode != null) {
                    switch (_actionCode) {
                        case crawlaction: {
                            final Collection<DigestURL> urls = new ArrayList<DigestURL>();
                            urls.add(new DigestURL(strUrl));
                            Switchboard.getSwitchboard().addToCrawler(urls, false);
                            break;
                        }
                        case indexaction: {
                            final Collection<DigestURL> urls = new ArrayList<DigestURL>();
                            urls.add(new DigestURL(strUrl));

                            Switchboard.getSwitchboard().addToIndex(urls, null, null, null, true);
                            break;
                        }
                        case crawllinksaction: {
                            final Collection<DigestURL> urls = new ArrayList<DigestURL>();
                            urls.add(new DigestURL(strUrl));
                            Switchboard.getSwitchboard().addToCrawler(urls, false);
                            Switchboard.getSwitchboard().heuristicSearchResults(strUrl);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            ConcurrentLog.logException(e);
        }
    }

}
