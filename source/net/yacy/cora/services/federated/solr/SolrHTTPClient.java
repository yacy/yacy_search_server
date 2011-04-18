/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This file was part of the solrj package and used the apache http client 3.1
 * It was modified and adopted to work with the apache http client 4.1
 * using the net.yacy.cora connection package of YaCy
 * Code modifications (C) under Apache License 2.0 by Michael Christen, 14.4.2011
 */

package net.yacy.cora.services.federated.solr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import net.yacy.cora.protocol.http.HTTPClient;

import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DefaultSolrParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.NamedList;

/**
 * The {@link SolrHTTPClient} uses the Apache Commons HTTP Client to connect to solr. 
 * <pre class="prettyprint" >SolrServer server = new CommonsHttpSolrServer( url );</pre>
 * 
 * @version $Id: CommonsHttpSolrServer.java 1067552 2011-02-05 23:52:42Z koji $
 * @since solr 1.3
 */
public class SolrHTTPClient extends SolrServer {
    private static final long serialVersionUID = -4532572298724852268L;

/**
   * User-Agent String as identified by the HTTP request by the {@link
   * org.apache.commons.httpclient.HttpClient HttpClient} to the Solr
   * server from the client.
   */
  public static final String AGENT = "Solr["+SolrHTTPClient.class.getName()+"] 1.0"; 

  public final static Charset utf8;
  static {
      utf8 = Charset.forName("UTF-8");
  }

  /**
   * The URL of the Solr server.
   */
  protected String _baseURL;
  
  /**
   * Default value: null / empty. <p/>
   * Parameters that are added to every request regardless.  This may be a place to add 
   * something like an authentication token.
   */
  protected ModifiableSolrParams _invariantParams;
  
  /**
   * Default response parser is BinaryResponseParser <p/>
   * This parser represents the default Response Parser chosen to
   * parse the response if the parser were not specified as part of
   * the request.
   * @see org.apache.solr.client.solrj.impl.BinaryResponseParser
   */
  protected ResponseParser _parser;

  /**
   * The RequestWriter used to write all requests to Solr
   * @see org.apache.solr.client.solrj.request.RequestWriter
   */
  protected RequestWriter requestWriter = new RequestWriter();
  
  /**  
   * @param solrServerUrl The URL of the Solr server.  For 
   * example, "<code>http://localhost:8983/solr/</code>"
   * if you are using the standard distribution Solr webapp 
   * on your local machine.
   */
  public SolrHTTPClient(String solrServerUrl) throws MalformedURLException {
    this(new URL(solrServerUrl));
  }

  /**
   * @param baseURL The URL of the Solr server.  For example,
   * "<code>http://localhost:8983/solr/</code>" if you are using the
   * standard distribution Solr webapp on your local machine.
   */
  public SolrHTTPClient(URL baseURL) 
  {
    this(baseURL, new BinaryResponseParser());
  }

  /**
   * @see #useMultiPartPost
   * @see #_parser
   */
  public SolrHTTPClient(URL baseURL, ResponseParser parser) {
    _baseURL = baseURL.toExternalForm();
    if( _baseURL.endsWith( "/" ) ) {
      _baseURL = _baseURL.substring( 0, _baseURL.length()-1 );
    }
    if( _baseURL.indexOf( '?' ) >=0 ) {
      throw new RuntimeException( "Invalid base url for solrj.  The base URL must not contain parameters: "+_baseURL );
    }
    _parser = parser;
  }
  
  //------------------------------------------------------------------------
  //------------------------------------------------------------------------

  /**
   * Process the request.  If {@link org.apache.solr.client.solrj.SolrRequest#getResponseParser()} is null, then use
   * {@link #getParser()}
   * @param request The {@link org.apache.solr.client.solrj.SolrRequest} to process
   * @return The {@link org.apache.solr.common.util.NamedList} result
   * @throws SolrServerException
   * @throws IOException
   *
   * @see #request(org.apache.solr.client.solrj.SolrRequest, org.apache.solr.client.solrj.ResponseParser)
   */
  @Override
  public NamedList<Object> request( final SolrRequest request ) throws SolrServerException, IOException
  {
    ResponseParser responseParser = request.getResponseParser();
    if (responseParser == null) {
      responseParser = _parser;
    }
    return request(request, responseParser);
  }

  
  public NamedList<Object> request(final SolrRequest request, ResponseParser processor) throws SolrServerException, IOException {
    SolrParams params = request.getParams();
    Collection<ContentStream> streams = requestWriter.getContentStreams(request);
    String path = requestWriter.getPath(request);
    if( path == null || !path.startsWith( "/" ) ) {
      path = "/select";
    }
    
    // The parser 'wt=' and 'version=' params are used instead of the original params
    ResponseParser parser = request.getResponseParser();
    if( parser == null ) {
        parser = _parser;
      }
    ModifiableSolrParams wparams = new ModifiableSolrParams();
    wparams.set( CommonParams.WT, parser.getWriterType() );
    wparams.set( CommonParams.VERSION, parser.getVersion());
    if( params == null ) {
      params = wparams;
    }
    else {
      params = new DefaultSolrParams( wparams, params );
    }
    
    if( _invariantParams != null ) {
      params = new DefaultSolrParams( _invariantParams, params );
    }
    
    
    byte[] result = null;
    HTTPClient client = new HTTPClient();
    if (SolrRequest.METHOD.POST == request.getMethod()) {
        boolean isMultipart = ( streams != null && streams.size() > 1 );
        if (streams == null || isMultipart) {
            String url = _baseURL + path;
            
            HashMap<String, ContentBody> parts = new HashMap<String, ContentBody>();
            Iterator<String> iter = params.getParameterNamesIterator();
            while (iter.hasNext()) {
                String p = iter.next();
                String[] vals = params.getParams(p);
                if (vals != null) {
                    for (String v : vals) {
                        if (isMultipart) {
                            parts.put(p, new StringBody(v, utf8));
                          } else {
                              if (url.indexOf('?') >= 0) url += "&" + p + "=" + v; else url += "?" + p + "=" + v;
                          }
                    }
                }
            }
            
            if (isMultipart) {
                for (ContentStream content : streams) {
                  parts.put(content.getName(), new InputStreamBody(content.getStream(), content.getContentType(), null));
                }
            }
            
            try {
                result = client.POSTbytes(url, parts, true);
            } finally {
                client.finish();
            }
        } else {
            // It has one stream, this is the post body, put the params in the URL
            String pstr = ClientUtils.toQueryString(params, false);
            String url = _baseURL + path + pstr;
            
            // Single stream as body
            // Using a loop just to get the first one
            final ContentStream[] contentStream = new ContentStream[1];
            for (ContentStream content : streams) {
              contentStream[0] = content;
              break;
            }
            result = client.POSTbytes(url, contentStream[0].getStream(), contentStream[0].getStream().available());
        }
    } else if (SolrRequest.METHOD.GET == request.getMethod()) {
        result = client.GETbytes( _baseURL + path + ClientUtils.toQueryString( params, false ));
    } else {
        throw new SolrServerException("Unsupported method: "+request.getMethod() );
    }

      int statusCode = client.getStatusCode();
      if (statusCode != 200) {
        throw new IOException("bad status code: " + statusCode + ", " + client.getHttpResponse().getStatusLine());
      }

      // Read the contents
      //System.out.println("SOLR RESPONSE: " + UTF8.String(result));
      InputStream respBody = new ByteArrayInputStream(result);
      return processor.processResponse(respBody, "UTF-8");
  }

  /*
   * The original code for the request method
  public NamedList<Object> request(final SolrRequest request, ResponseParser processor) throws SolrServerException, IOException {
    HttpMethod method = null;
    InputStream is = null;
    SolrParams params = request.getParams();
    Collection<ContentStream> streams = requestWriter.getContentStreams(request);
    String path = requestWriter.getPath(request);
    if( path == null || !path.startsWith( "/" ) ) {
      path = "/select";
    }
    
    ResponseParser parser = request.getResponseParser();
    if( parser == null ) {
      parser = _parser;
    }
    
    // The parser 'wt=' and 'version=' params are used instead of the original params
    ModifiableSolrParams wparams = new ModifiableSolrParams();
    wparams.set( CommonParams.WT, parser.getWriterType() );
    wparams.set( CommonParams.VERSION, parser.getVersion());
    if( params == null ) {
      params = wparams;
    }
    else {
      params = new DefaultSolrParams( wparams, params );
    }
    
    if( _invariantParams != null ) {
      params = new DefaultSolrParams( _invariantParams, params );
    }

    int tries = _maxRetries + 1;
    try {
      while( tries-- > 0 ) {
        // Note: since we aren't do intermittent time keeping
        // ourselves, the potential non-timeout latency could be as
        // much as tries-times (plus scheduling effects) the given
        // timeAllowed.
        try {
          if( SolrRequest.METHOD.GET == request.getMethod() ) {
            if( streams != null ) {
              throw new SolrException( SolrException.ErrorCode.BAD_REQUEST, "GET can't send streams!" );
            }
            method = new GetMethod( _baseURL + path + ClientUtils.toQueryString( params, false ) );
          }
          else if( SolrRequest.METHOD.POST == request.getMethod() ) {

            String url = _baseURL + path;
            boolean isMultipart = ( streams != null && streams.size() > 1 );

            if (streams == null || isMultipart) {
              PostMethod post = new PostMethod(url);
              post.getParams().setContentCharset("UTF-8");
              if (!this.useMultiPartPost && !isMultipart) {
                post.addRequestHeader("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");
              }

              List<Part> parts = new LinkedList<Part>();
              Iterator<String> iter = params.getParameterNamesIterator();
              while (iter.hasNext()) {
                String p = iter.next();
                String[] vals = params.getParams(p);
                if (vals != null) {
                  for (String v : vals) {
                    if (this.useMultiPartPost || isMultipart) {
                      parts.add(new StringPart(p, v, "UTF-8"));
                    } else {
                      post.addParameter(p, v);
                    }
                  }
                }
              }

              if (isMultipart) {
                int i = 0;
                for (ContentStream content : streams) {
                  final ContentStream c = content;

                  String charSet = null;
                  String transferEncoding = null;
                  parts.add(new PartBase(c.getName(), c.getContentType(),
                      charSet, transferEncoding) {
                    @Override
                    protected long lengthOfData() throws IOException {
                      return c.getSize();
                    }

                    @Override
                    protected void sendData(OutputStream out)
                        throws IOException {
                      InputStream in = c.getStream();
                      try {
                        IOUtils.copy(in, out);
                      } finally {
                        in.close();
                      }
                    }
                  });
                }
              }
              if (parts.size() > 0) {
                post.setRequestEntity(new MultipartRequestEntity(parts
                    .toArray(new Part[parts.size()]), post.getParams()));
              }

              method = post;
            }
            // It is has one stream, it is the post body, put the params in the URL
            else {
              String pstr = ClientUtils.toQueryString(params, false);
              PostMethod post = new PostMethod(url + pstr);

              // Single stream as body
              // Using a loop just to get the first one
              final ContentStream[] contentStream = new ContentStream[1];
              for (ContentStream content : streams) {
                contentStream[0] = content;
                break;
              }
              if (contentStream[0] instanceof RequestWriter.LazyContentStream) {
                post.setRequestEntity(new RequestEntity() {
                  public long getContentLength() {
                    return -1;
                  }

                  public String getContentType() {
                    return contentStream[0].getContentType();
                  }

                  public boolean isRepeatable() {
                    return false;
                  }

                  public void writeRequest(OutputStream outputStream) throws IOException {
                    ((RequestWriter.LazyContentStream) contentStream[0]).writeTo(outputStream);
                  }
                }
                );

              } else {
                is = contentStream[0].getStream();
                post.setRequestEntity(new InputStreamRequestEntity(is, contentStream[0].getContentType()));
              }
              method = post;
            }
          }
          else {
            throw new SolrServerException("Unsupported method: "+request.getMethod() );
          }
        }
        catch( NoHttpResponseException r ) {
          // This is generally safe to retry on
          method.releaseConnection();
          method = null;
          if(is != null) {
            is.close();
          }
          // If out of tries then just rethrow (as normal error).
          if( ( tries < 1 ) ) {
            throw r;
          }
          //log.warn( "Caught: " + r + ". Retrying..." );
        }
      }
    }
    catch( IOException ex ) {
      throw new SolrServerException("error reading streams", ex );
    }

    method.setFollowRedirects( _followRedirects );
    method.addRequestHeader( "User-Agent", AGENT );
    if( _allowCompression ) {
      method.setRequestHeader( new Header( "Accept-Encoding", "gzip,deflate" ) );
    }

    try {
      // Execute the method.
      //System.out.println( "EXECUTE:"+method.getURI() );

      int statusCode = _httpClient.executeMethod(method);
      if (statusCode != HttpStatus.SC_OK) {
        StringBuilder msg = new StringBuilder();
        msg.append( method.getStatusLine().getReasonPhrase() );
        msg.append( "\n\n" );
        msg.append( method.getStatusText() );
        msg.append( "\n\n" );
        msg.append( "request: "+method.getURI() );
        throw new SolrException(statusCode, java.net.URLDecoder.decode(msg.toString(), "UTF-8") );
      }

      // Read the contents
      String charset = "UTF-8";
      if( method instanceof HttpMethodBase ) {
        charset = ((HttpMethodBase)method).getResponseCharSet();
      }
      InputStream respBody = method.getResponseBodyAsStream();
      // Jakarta Commons HTTPClient doesn't handle any
      // compression natively.  Handle gzip or deflate
      // here if applicable.
      if( _allowCompression ) {
        Header contentEncodingHeader = method.getResponseHeader( "Content-Encoding" );
        if( contentEncodingHeader != null ) {
          String contentEncoding = contentEncodingHeader.getValue();
          if( contentEncoding.contains( "gzip" ) ) {
            //log.debug( "wrapping response in GZIPInputStream" );
            respBody = new GZIPInputStream( respBody );
          }
          else if( contentEncoding.contains( "deflate" ) ) {
            //log.debug( "wrapping response in InflaterInputStream" );
            respBody = new InflaterInputStream(respBody);
          }
        }
        else {
          Header contentTypeHeader = method.getResponseHeader( "Content-Type" );
          if( contentTypeHeader != null ) {
            String contentType = contentTypeHeader.getValue();
            if( contentType != null ) {
              if( contentType.startsWith( "application/x-gzip-compressed" ) ) {
                //log.debug( "wrapping response in GZIPInputStream" );
                respBody = new GZIPInputStream( respBody );
              }
              else if ( contentType.startsWith("application/x-deflate") ) {
                //log.debug( "wrapping response in InflaterInputStream" );
                respBody = new InflaterInputStream(respBody);
              }
            }
          }
        }
      }
      return processor.processResponse(respBody, charset);
    }
    catch (HttpException e) {
      throw new SolrServerException( e );
    }
    catch (IOException e) {
      throw new SolrServerException( e );
    }
    finally {
      method.releaseConnection();
      if(is != null) {
        is.close();
      }
    }
  }

   */
  
  //-------------------------------------------------------------------
  //-------------------------------------------------------------------
  
  /**
   * Retrieve the default list of parameters are added to every request regardless.
   * 
   * @see #_invariantParams
   */
  public ModifiableSolrParams getInvariantParams()
  {
    return _invariantParams;
  }

  public String getBaseURL() {
    return _baseURL;
  }

  public void setBaseURL(String baseURL) {
    this._baseURL = baseURL;
  }

  public ResponseParser getParser() {
    return _parser;
  }

  /**
   * Note: This setter method is <b>not thread-safe</b>.
   * @param processor Default Response Parser chosen to parse the response if the parser were not specified as part of the request.
   * @see  org.apache.solr.client.solrj.SolrRequest#getResponseParser()
   */
  public void setParser(ResponseParser processor) {
    _parser = processor;
  }


  public void setRequestWriter(RequestWriter requestWriter) {
    this.requestWriter = requestWriter;
  }

  /**
   * Adds the documents supplied by the given iterator.
   *
   * @param docIterator  the iterator which returns SolrInputDocument instances
   *
   * @return the response from the SolrServer
   */
  public UpdateResponse add(Iterator<SolrInputDocument> docIterator)
          throws SolrServerException, IOException {
    UpdateRequest req = new UpdateRequest();
    req.setDocIterator(docIterator);    
    return req.process(this);
  }

  /**
   * Adds the beans supplied by the given iterator.
   *
   * @param beanIterator  the iterator which returns Beans
   *
   * @return the response from the SolrServer
   */
  public UpdateResponse addBeans(final Iterator<?> beanIterator)
          throws SolrServerException, IOException {
    UpdateRequest req = new UpdateRequest();
    req.setDocIterator(new Iterator<SolrInputDocument>() {

      public boolean hasNext() {
        return beanIterator.hasNext();
      }

      public SolrInputDocument next() {
        Object o = beanIterator.next();
        if (o == null) return null;
        return getBinder().toSolrInputDocument(o);
      }

      public void remove() {
        beanIterator.remove();
      }
    });
    return req.process(this);
  }
}
