#!/usr/bin/perl -w
#
# This is an URL Redirector Script for squid that can be 
# used to bundle YaCy and Squid together via the squid
# redirector support.
# See: http://www.squid-cache.org/Doc/FAQ/FAQ-15.html
#
# This scripts forwards URLs from squid to YaCy where the
# URLs are used to download and index the content of the URLs.
use strict;
use Socket qw(:DEFAULT :crlf);
use IO::Handle;
use Digest::MD5;

# setting administrator username + pwd, hostname + port
my $user = "user";
my $pwd  = "";
my $host = "localhost";
my $port = "8080";

my $allowCgi = 0;
my $allowPost = 0;

my @mediaExt;
my @requestData;

$|=1;

sub isCGI {
 my $url = lc shift;
 return ((rindex $url, ".cgi") != -1)         || 
        ((rindex $url, ".exe") != -1)         ||
        ((rindex $url, ";jsessionid=") != -1) ||
        ((rindex $url, "sessionid/") != -1)   || 
        ((rindex $url, "phpsessid=") != -1);
}

sub isPOST {
 my $url = lc shift;
 return ((rindex $url, "?") != -1)         || 
        ((rindex $url, "&") != -1);
}

sub isMediaExt {
 my $url = $_[0];
 my @extList = @{$_[1]};
 my $pos = rindex $url, ".";
 
 if ($pos != -1) {
    my $ext = substr($url,$pos+1,length($url));
    my @match = grep(/$ext/,@extList);
    return scalar(@match);
 }
 return 0;
}

my ($bytes_out,$bytes_in) = (0,0);
my ($msg_in,$msg_out);

my $protocol = getprotobyname('tcp');
$host = inet_aton($host) or die "$host: unknown host";

socket(SOCK, AF_INET, SOCK_STREAM, $protocol) or die "socket() failed: $!";
my $dest_addr = sockaddr_in($port,$host);
connect(SOCK,$dest_addr) or die("connect() failed: $!");

# enabling autoflush
SOCK->autoflush(1);

# sending the REDIRECTOR command to yacy to enable the proper
# command handler
print SOCK "REDIRECTOR".CRLF;

# Doing authentication
my $ctx = Digest::MD5->new;
$ctx->add($user.":".$pwd);
my $md5Pwd = $ctx->hexdigest;

print SOCK "USER ".$user.CRLF;
print SOCK "PWD ".$md5Pwd.CRLF;

# Getting a list of file extensions that should be ignored
print SOCK "MEDIAEXT".CRLF;
$msg_in = lc <SOCK>;
chomp $msg_in;
@mediaExt = split(/,\s*/, $msg_in);

# 1) Reading URLs from stdIn 
# 2) Send it to Yacy
# 3) Receive response from YaCy
# 4) Print response to StdOut
while (defined($msg_out = <>)) {
    chomp $msg_out;
    
    # splitting request into it's various parts 
    #
    # One squid redirector request line typically looks like this:
    # http://www.pageresource.com/styles/tuts.css 192.168.0.5/- - GET
    @requestData =  split(/\s+/, $msg_out);
    
    # testing if the URL is CGI
    if (!$allowCgi && isCGI($requestData[0])) { 
     print STDOUT CRLF;
     print STDERR "URL is cgi: ".$msg_out.CRLF;
     next; 
    }
    
    # testing if the URL is a POST request
    if (!$allowPost && isPOST($requestData[0])){ 
     print STDOUT CRLF;
     print STDERR "URL is post: ".$msg_out.CRLF;
     next; 
    }
    
    # testing if the requested content is a media content
    if (isMediaExt($requestData[0],\@mediaExt)) {
     print STDOUT CRLF;
     print STDERR "URL has media extension: ".$msg_out.CRLF;
     next; 
    }
    
    # sending the whole request line to YaCy
    $msg_out .= CRLF;
    print SOCK $msg_out;
    
    # reading the response
    if (defined($msg_in = <SOCK>)) {
       print STDOUT $msg_in;
    } else {
      print STDERR "Socket closed".CRLF;
      close SOCK;
      exit(1);
    }

    $bytes_out += length($msg_out);
    $bytes_in  += length($msg_in);
}
print SOCK "EXIT".CRLF;

close SOCK;
print STDERR "bytes_sent = $bytes_out, bytes_received = $bytes_in\n";

