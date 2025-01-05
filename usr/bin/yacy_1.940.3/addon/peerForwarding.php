<?php
	
	/* by Allo, KarlchenOfHell */
	
	$page = 'error.html';
	$peername = 'Error';
	$seedurl = 'http://www.anomic.de/yacy/seed.txt';	// remote seed-file
	$seedfile = 'seed.txt';							// local cache file, needs write access
	$hostlength = 24; 							// EDIT THIS! length of the hostname + 1 (strlen(".peer.karlchenofhell.org"))
	
	function get($url, $filename) {
		if(!file_exists($filename) || (time()-filectime($filename)) > 600){
			$content=file_get_contents($url);
			$file=fopen($filename, 'w');
			fwrite($file, $content);
			fclose($file);
		}else{
			$content=file_get_contents($filename);
		}
		return $content;
	}
	
	class peer {
        
        var $seed;
        
        function peer($seed) {
            switch (substr($seed, 0, 1)) {
                case 'p': $plainlist = substr($seed, 2); break;            // plain text
                case 'b': $plainlist = base64_decode(substr($seed, 2)); break;    // base64-encoded
                default: $plainlist = substr($seed, 2); break;
            }
            $plainlist = substr($plainlist, 1, -1);        // kill '{' on beginning and '}' at the end
            $list = explode(',', $plainlist);
            $r = array();
            foreach ($list as $value) {
                $equal = strpos($value, '=');
                $r[substr($value, 0, $equal)] = substr($value, $equal + 1);
            }
            $this->seed = $r;
        }
        
        function getHash()         { return $this->seed['Hash']; }
        function getIPType()       { return $this->seed['IPType']; }
        function getTags()         { return $this->seed['Tags']; }
        function getPort()         { return $this->seed['Port']; }
        function getIP()           { return $this->seed['IP']; }
        function getRI()           { return $this->seed['rI']; }
        function getUptime()       { return $this->seed['Uptime']; }
        function getVersion()      { return $this->seed['Version']; }
        function getUTC()          { return $this->seed['UTC']; }
        function getPeerType()     { return $this->seed['PeerType']; }
        function getSI()           { return $this->seed['sI']; }
        function getLastSeen()     { return $this->seed['LastSeen']; }
        function getName()         { return $this->seed['Name']; }
        function getCCount()       { return $this->seed['CCount']; }
        function getSCount()       { return $this->seed['SCount']; }
        function getNews()         { return $this->seed['news']; }
        function getUSpeed()       { return $this->seed['USpeed']; }
        function getCRTCount()     { return $this->seed['CRTCnt']; }
        function getCRWCount()     { return $this->seed['CRWCnt']; }
        function getBirthDate()    { return $this->seed['BDate']; }
        function getLinks()        { return $this->seed['LCount']; }
        function getRU()           { return $this->seed['rU']; }
        function getWords()        { return $this->seed['ICount']; }
        function getSU()           { return $this->seed['sU']; }
        function getISpeed()       { return $this->seed['ISpeed']; }
        function getNCount()       { return $this->seed['NCount']; }
        function getFlags()        { return $this->seed['Flags']; }
    }
    
    function decodeSeedList($list) {
        $seeds = explode("\n", $list);
        $r = array();
        foreach ($seeds as $seed) {
            $peer = new peer($seed);
            $r[strtolower($peer->getName())] = $peer;
        }
        return $r;
    }

	###peername.yacypeer.dyndns.org
	###rewrite:
	###RewriteCond %{REQUEST_URI} /error.html
	###RewriteRule ^/(.*) /error.html [L]
	###RewriteRule ^/(.*) /index.php?url=$1 [L]
	$name = $_SERVER['SERVER_NAME'];
	$name = substr($name, 0, strlen($name) - $hostlength);
	
	###domain.org/peername/*
	###rewrite:
	###RewriteRule ^/(.*) /error.html [L]
	###RewriteRule ^/([^\/]*)/(.*) /index.php?name=$1&url=$2 [L]
	#$name=$_GET['name'];
	
	$seedfile = get($seedurl, $seedfile);
	$peers = decodeSeedList($seedfile);
	
	$peer = $peers[strtolower($name)];
	
	

	
	if (strlen($name) == 0 || $peer == null) {
		// peer-list
?>
<html>
	<head>
		<title>Active YaCy-Peers</title>
	</head>
	<body>
		<table border="2" cellpadding="2" cellspacing="0">
			<tr>
				<th>Name</th>
				<th>Uptime</th>
			</tr><?
		foreach ($peers as $peer) {
			echo '
			<tr>
				<td><a href="http://'. $peer->getIP() .':'. $peer->getPort() .'">'. $peer->getName() .'</a></td>
				<td>'. $peer->getUptime() .'</td>
			</tr>';
		}?>
		</table>
	</body>
</html>
<?
	} else {
		
		if ($peer) {
			$peername=$peer->getName();
			$page='http://'. $peer->getIP() .':'.$peer->getPort() .'/';
		}
		
?>
<html>
	<head>
		<title><? echo $peername; ?></title>
	</head>
	<frameset rows="*">
		<frame src="<? echo $page/*.$_GET['url']*/; ?>" />
	</frameset>
</html>
<?
	}