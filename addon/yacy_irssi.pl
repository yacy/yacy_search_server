#!/usr/bin/perl

use LWP::UserAgent;

$VERSION = "0.01";
%IRSSI = (
	  authors     => "Alexander Schier",
	  contact     => "",
	  name        => "yacy_script",
	  description => "A Yacy Script for Irssi",
	  license     => "GPL",
	  url         => "http://www.yacy-websuche.de",
	  changed     => "Thu Mar 16 2006"
	  );

use Irssi;
use strict;

Irssi::settings_add_str("yacy_script.pl", "yacy_host", "localhost");
Irssi::settings_add_int("yacy_script.pl", "yacy_port", 8080);
Irssi::settings_add_str("yacy_script.pl", "yacy_user", "admin");
Irssi::settings_add_str("yacy_script.pl", "yacy_password", "");

my $ua = LWP::UserAgent->new;
$ua->timeout(10);
$ua->env_proxy;

sub yacy_show($$$){
	my $host=Irssi::settings_get_str("yacy_host");
	my $port=Irssi::settings_get_int("yacy_port");
	my $user=Irssi::settings_get_str("yacy_user");
	my $pass=Irssi::settings_get_str("yacy_password");

	my $BASEURL="http://".$user.":".$pass."@".$host.":".$port;
	my $response = $ua->get($BASEURL."/xml/status_p.xml");
	my @content=$response->content;
	my $PPM=0;
	foreach my $line (@content){
		if($line=~/<ppm>(.*?)<\/ppm>/){
			$PPM=$1;
		}
	}
	#Irssi::active_win->command("/me is now crawling with YaCy at $PPM pages per minute.");
	Irssi::active_win->command("/me 's YaCy is crawling at $PPM pages per minute.");
}  

Irssi::command_bind('yacy_show', \&yacy_show);
