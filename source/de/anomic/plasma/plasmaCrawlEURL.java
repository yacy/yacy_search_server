// plasmaCrawlEURL.java
// (C) 2004 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 09.08.2004 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.plasma;

public class plasmaCrawlEURL {

    /* =======================================================================
     * Failure reason constants
     * ======================================================================= */

    // invalid urls
    public static final String DENIED_URL_NULL = "denied_(url_null)";
    public static final String DENIED_MALFORMED_URL = "denied_(malformed_url)";
    public static final String DENIED_UNSUPPORTED_PROTOCOL = "denied_(unsupported_protocol)";
    public static final String DENIED_IP_ADDRESS_NOT_IN_DECLARED_DOMAIN = "denied_(address_not_in_declared_domain)";
    public static final String DENIED_LOOPBACK_IP_ADDRESS = "denied_(loopback_ip_address)";
    public static final String DENIED_CACHEFILE_PATH_TOO_LONG = "denied_(cachefile_path_too_long)";
    public static final String DENIED_INVALID_CACHEFILE_PATH = "denied_(invalid_cachefile_path)";    
    
    // blacklisted/blocked urls
    public static final String DENIED_URL_IN_BLACKLIST = "denied_(url_in_blacklist)";
    public static final String DENIED_URL_DOES_NOT_MATCH_FILTER = "denied_(does_not_match_filter)";
    public static final String DENIED_CGI_URL = "denied_(cgi_url)";
    public static final String DENIED_POST_URL = "denied_(post_url)";
    public static final String DENIED_NO_MATCH_WITH_DOMAIN_FILTER = "denied_(no_match_with_domain_filter)";
    public static final String DENIED_DOMAIN_COUNT_EXCEEDED = "denied_(domain_count_exceeded)";    
    public static final String DENIED_ROBOTS_TXT = "denied_(robots.txt)";
    
    // wrong content
    public static final String DENIED_WRONG_MIMETYPE_OR_EXT = "denied_(wrong_mimetype_or_extension)";
    public static final String DENIED_UNSUPPORTED_CHARSET = "denied_(unsupported_charset)";
    public static final String DENIED_REDIRECTION_HEADER_EMPTY = "denied_(redirection_header_empty)";
    public static final String DENIED_REDIRECTION_COUNTER_EXCEEDED = "denied_(redirection_counter_exceeded)";
    public static final String DENIED_WRONG_HTTP_STATUSCODE = "denied_(wrong_http_status_code_";
    public static final String DENIED_CONTENT_DECODING_ERROR = "denied_(content_decoding_error)";
    public static final String DENIED_FILESIZE_LIMIT_EXCEEDED = "denied_(filesize_limit_exceeded)";
    public static final String DENIED_FILESIZE_UNKNOWN = "denied_(filesize_unknown)";
    
    // network errors
    public static final String DENIED_UNKNOWN_HOST = "denied_(unknown_host)";
    public static final String DENIED_NO_ROUTE_TO_HOST = "denied_(no_route_to_host)"; 
    public static final String DENIED_NETWORK_IS_UNREACHABLE = "denied_(Network_is_unreachable)"; 
    
    // connection errors
    public static final String DENIED_CONNECTION_ERROR = "denied_(connection_error)";
    public static final String DENIED_CONNECTION_BIND_EXCEPTION = "denied_(connection_bind_exception)";
    public static final String DENIED_CONNECTION_TIMEOUT = "denied_(connection_timeout)";
    public static final String DENIED_CONNECTION_REFUSED = "denied_(connection_refused)";    
    public static final String DENIED_SSL_UNTRUSTED_CERT = "denied_(No_trusted_ssl_certificate_found)";

    // double registered errors
    public static final String DOUBLE_REGISTERED = "double_(registered_in_";
    
    // server errors
    public static final String DENIED_OUT_OF_DISK_SPACE = "denied_(out_of_disk_space)";
    public static final String DENIED_SERVER_SHUTDOWN = "denied_(server_shutdown)";
    public static final String DENIED_SERVER_LOGIN_FAILED = "denied_(server_login_failed)";
    public static final String DENIED_SERVER_TRASFER_MODE_PROBLEM = "denied_(server_transfermode_problem)";
    public static final String DENIED_SERVER_DOWNLOAD_ERROR = "denied_(server_download_error)";
    
    // Parser errors
    public static final String DENIED_PARSER_ERROR = "denied_(parser_error)";
    public static final String DENIED_DOCUMENT_ENCRYPTED = "denied_(document_encrypted)";
    public static final String DENIED_NOT_PARSEABLE_NO_CONTENT = "denied_(not_parseabel_no_content)";
    
    // indexing errors
    public static final String DENIED_UNSPECIFIED_INDEXING_ERROR = "denied_(unspecified_indexing_error)";
    public static final String DENIED_UNKNOWN_INDEXING_PROCESS_CASE = "denied_(unknown_indexing_process_case)";

}
