<?xml version="1.0" encoding="utf-8" ?>
<?xml-stylesheet type='text/xsl' href='/rss.xsl' version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:dc="http://purl.org/dc/elements/1.1/" version="1.0">
	<xsl:output method="html"/>
	<xsl:template match='/rss'>
		<html>
			<head>
				<title><xsl:value-of select='channel/title' /></title>
				<meta http-equiv="content-type" content="text/html; charset=utf-8" />
				<link rel="shortcut icon" href="favicon.ico" />
				<style type="text/css">
					@import "/env/style.css";
					@import "/env/base.css";
				</style>
			</head>
			<body>
				<div align="center">
					<img src="env/grafics/kaskelix.png"/><br/>
					<h1><xsl:value-of select='channel/title' /></h1>				
				</div>	
				<p>
					<xsl:apply-templates select='channel/item' />
				</p>						
			</body>
		</html>
	</xsl:template>
	
	<xsl:template match='item'>
		<div class="searchresults">
			<h4 class="linktitle"><a href="{link}" ><xsl:value-of select='title'/></a></h4>
			<p class="snippet"><span class="snippetLoaded"><xsl:value-of select='description'/></span></p>
			<p class="url"><a href="{link}" ><xsl:value-of select='link' /></a></p>
			<p class="urlinfo"><xsl:value-of select='pubDate' /></p>
		</div>
	</xsl:template>
	
</xsl:stylesheet>