/**
 *  OllamaTagsServlet
 *  Copyright 2025 by Michael Peter Christen
 *  First released 16.11.2025 at https://yacy.net
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

package net.yacy.http.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.servlet.cache.Method;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.LLM;

public class OllamaTagsServlet extends HttpServlet {

    private static final long serialVersionUID = 3411344789759603107L;

    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        response.setContentType("application/json;charset=utf-8");

        HttpServletResponse hresponse = (HttpServletResponse) response;
        HttpServletRequest hrequest = (HttpServletRequest) request;

        // Add CORS headers
        hresponse.setHeader("Access-Control-Allow-Origin", "*");
        hresponse.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
        hresponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        final Method reqMethod = Method.getMethod(hrequest.getMethod());

        // We expect a POST request
        if (reqMethod != Method.GET) {
            hresponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        
        ServletOutputStream out = response.getOutputStream();
        try {
            JSONObject data = new JSONObject();
            JSONArray models = new JSONArray();
            data.put("models", models);
            
            // the model names are not the real model names but the usage classes that can be assigned to a model
            for (LLM.LLMUsage usage: LLM.LLMUsage.values()) {
                String modelName = usage.name();
                JSONObject model = new JSONObject();
                model.put("name", modelName);
                model.put("model", modelName);
                models.put(model);
            }
        
            out.println(data.toString());
            out.flush();
            hresponse.setStatus(200);
            out.close(); // close this here to end transmission
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
