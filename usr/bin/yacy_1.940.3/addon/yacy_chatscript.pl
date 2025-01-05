#!/usr/bin/perl

$VERSION = "0.05";
%IRSSI = (
	  authors     => "Alexander Schier & Robert Weidlich",
	  contact     => "",
	  name        => "YaCy Script",
	  description => "A script to monitor and control YaCy",
	  license     => "GPL",
	  url         => "http://www.yacy-websuche.de",
	  changed     => "Wed Apr 05 2006"
	  );

#use Irssi;
use strict;
use XML::Simple;
use LWP::Simple;
use Data::Dumper;
use vars qw($VERSION %IRSSI);

my $help = "/yacy show ppm|peer|version|network\n".
	   "      set host|port|user|pass value\n".
	   "      get host|port|user|pass\n".
	   "      help";
our %cmds = 
(
	show => ["ppm","peer","version","network", "stats"],
	set => ["user","pass","host","port"],
	help => []
);
my $prog;

if ( defined(&Xchat::print) ) {
	$prog = "xchat";
	Xchat::register($IRSSI{'name'},$VERSION,$IRSSI{'description'});
} elsif ( defined(&Irssi::print) ) {
	$prog = "irssi";
}

sub setting_init() {
	if ($prog eq "irssi") {
		Irssi::settings_add_str("yacy_script.pl", "yacy_host", "localhost");
		Irssi::settings_add_int("yacy_script.pl", "yacy_port", 8090);
		Irssi::settings_add_str("yacy_script.pl", "yacy_user", "admin");
		Irssi::settings_add_str("yacy_script.pl", "yacy_pass", "");
		Irssi::settings_add_int("yacy_script.pl", "yacy_statusbarupdate_interval", 60);
	} elsif ($prog eq "xchat") {
		if ( ! -e Xchat::get_info('xchatdir')."/yacy.xml" ) {
			my $data = {
				host => "localhost",
				port => "8090",
				user => "admin",
				pass => ""
			};
			XMLout($data, NoAttr => 1, OutputFile => Xchat::get_info('xchatdir')."/yacy.xml");
		}
		
	}
}

sub setting_set($$) {
	if ($prog eq "xchat") {
		my $data = XMLin(Xchat::get_info('xchatdir')."/yacy.xml");
		$data->{$_[0]} = $_[1];
		open my $fh, '>', Xchat::get_info('xchatdir')."/yacy.xml";
		XMLout($data, NoAttr => 1, OutputFile => $fh);
		close $fh;
	} elsif ($prog eq "irssi") {
		Irssi::settings_set_str("yacy_".$_[0],$_[1]);
	}
}

sub setting_get($) {
	if ($prog eq "xchat") {
		my $data = XMLin(Xchat::get_info('xchatdir')."/yacy.xml");
		return $data->{$_[0]};
	} elsif ($prog eq "irssi") {
		return Irssi::settings_get_str("yacy_".$_[0]);
	} 
}

sub get_network {
	my $host = setting_get("host");
	my $port = setting_get("port");
	my $user = setting_get("user");
	my $pass = setting_get("pass");
	my $doc=get('http://'.$user.':'.$pass.'@'.$host.':'.$port.'/Network.xml');
	if($doc){
		return XMLin($doc);
	}
	return 0;
}

sub yacy($$$) {
	my ($cmd, $arg, $arg2, $prnt, $output);
	if ($prog eq "irssi") {
		($cmd,$arg,$arg2)=split / /, shift;
	} elsif ($prog eq "xchat") {
		$cmd = $_[0][1];
		$arg = $_[0][2];
		$arg2 = $_[0][3];
	}
	if ($cmd eq "show") {
		my $data=get_network();
		if ( ! $data ) { 
			$prnt = "Peer is not running.";
		} else {
			if ($arg eq "ppm") {
				$output = "is now crawling with YaCy at $data->{'your'}->{'ppm'} pages per minute.";
			} elsif ($arg eq "peer") {	
				$output = "operates the $data->{'your'}->{'type'} YaCy peer $data->{'your'}->{'name'}, which is running $data->{'your'}->{'uptime'}";
			} elsif ($arg eq "version") {
				$output = "uses YaCy version $data->{'your'}->{'version'}";
			} elsif ($arg eq "network") {
				$output = "'s peer currently knows $data->{'active'}->{'count'} senior and $data->{'potential'}->{'count'} junior peers";
			} elsif ($arg eq "stats") {
				$output = "'s peer stores $data->{'your'}->{'links'} links and $data->{'your'}->{'words'} words";
			} else {
				$prnt="Unknown argument: \"$arg\"\n$help";
			}
		}
	} elsif ($cmd eq "set") {
		if ($arg) {
			if ($arg2) {
				setting_set($arg,$arg2);
			} else {
				$prnt = "$arg is currently set to \"".setting_get($arg)."\"";
			}
		} else {
			$prnt = "Argument required\n$help";
		}
	} elsif ($cmd eq "help") {
		$prnt=$help;
	} else {
		$prnt="Unknown command: \"$cmd\"\n$help";
	}
	if ( $prog eq "irssi" ) {
		Irssi::active_win->command("/me $output") if ($output);
		Irssi::active_win->print($prnt) if ($prnt);
	} elsif ( $prog eq "xchat" ) {
		Xchat::print($prnt) if ($prnt);
		Xchat::command("me $output") if ($output);
		return 3;
	}
}  

sub cmd_help() {
	my ($arg) = @_;
	if ( $arg =~ /(yacy)/ ) {
		Irssi::print($help);
		Irssi::signal_stop();
	}
}

sub signal_complete_word {
	my ($list, $window, $word, $linestart, $want_space) = @_;
	if($linestart =~ /\/yacy/){
		Irssi::signal_stop();
		my @words=split(/ /, $linestart);
		if(@words == 1){
			my @cmds2=keys(%cmds);
			foreach (@cmds2){
				if($_ =~/^$word/i){
					push(@$list, $_);
				}
			}
		}elsif(@words == 2){
			my @cmds2=$cmds{@words[1]};
			for my $i (0 .. $#{$cmds2[0]} ) {
				if($cmds2[0][$i] =~/^$word/i){
					push(@$list, $cmds2[0][$i]);
				}
			}
		}
		
	}
}

# this is a irssi only section #
my ($irssi_links, $irssi_words, $irssi_ppm)=(0,0,0);;
sub irssi_init_statusbar {
	my $updateinterval = setting_get("statusbarupdate_interval"); #XXX: this does not work, if the option isn't set manually?!
	if( $updateinterval > 0 && get_network()){ #only, if Network.xml can be loaded and the interval is not zero
		Irssi::statusbar_item_register("yacyLinks", undef, "irssi_statusbar_yacyLinks");
		Irssi::statusbar_item_register("yacyWords", undef, "irssi_statusbar_yacyWords");
		Irssi::statusbar_item_register("yacyPPM", undef, "irssi_statusbar_yacyPPM");
		Irssi::timeout_add($updateinterval * 1000, "irssi_update_statusbar", undef);
		irssi_update_statusbar(); #initial update

		#TODO: Some way to add this to the statusbar (or create a own one), without
		#beeing obstrusive.
	}
}

sub irssi_update_statusbar {
	$data=get_network();
	if($data){
		$irssi_links=$data->{'your'}->{'links'};
		Irssi::statusbar_items_redraw('yacyLinks');
		$irssi_words=$data->{'your'}->{'words'};
		Irssi::statusbar_items_redraw('yacyWords');
		$irssi_ppm=$data->{'your'}->{'ppm'};
		Irssi::statusbar_items_redraw('yacyPPM');
	}
}

# redraw handlers #
sub irssi_statusbar_yacyLinks {
	my ($item, $get_size_only) = @_;
	$item->default_handler($get_size_only, " ".$irssi_links, undef, 1);
}
sub irssi_statusbar_yacyWords {
	my ($item, $get_size_only) = @_;
	$item->default_handler($get_size_only, " ".$irssi_words, undef, 1);
}
sub irssi_statusbar_yacyPPM {
	my ($item, $get_size_only) = @_;
	$item->default_handler($get_size_only, " ".$irssi_ppm, undef, 1);
}
# end irssi only #

setting_init();
if ( $prog eq "irssi" ) {
	Irssi::command_bind("help","cmd_help", "Irssi commands");
	Irssi::command_bind('yacy', \&yacy);
	Irssi::signal_add('complete word', 'signal_complete_word');

	#irssi only
	irssi_init_statusbar();
} elsif ( $prog eq "xchat") {
	Xchat::hook_command("yacy","yacy",{help_text => $help});
}
