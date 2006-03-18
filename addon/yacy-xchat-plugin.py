import xchat
import urllib
import re
import string

__module_name__ = "yacy" 
__module_version__ = "0.1" 
__module_description__ = "Shows yacys current PPM"

user = "admin"
password = "password"
host = "localhost:8080"
def yacy_ppm(word, word_eol, userdata):
  for line in urllib.urlopen("http://"+user+":"+password+"@"+host+"/xml/status_p.xml").readlines():
    if re.compile("<ppm>").search(line):
      xchat.command("me 's YaCy is crawling at "+line.strip().strip("<ppm/>")+" pages per minute.")
  return xchat.EAT_ALL
      
xchat.hook_command("YACY_SHOW",yacy_ppm,help="/yacy_show - shows the current ppm")      
