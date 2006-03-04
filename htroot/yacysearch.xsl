<?xml version="1.0" encoding="utf-8" ?>
<?xml-stylesheet type='text/xsl' href='/rss.xsl' version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:dc="http://purl.org/dc/elements/1.1/" version="1.0">
	<xsl:template match='/rss'>
		<html>
			<head>
				<title><xsl:value-of select='channel/title' /></title>
				<meta http-equiv="content-type" content="text/html; charset=utf-8" />
				<link rel="shortcut icon" href="favicon.ico" />
				<style type="text/css">
					@import "/env/style.css";
				</style>
			</head>
			<body>
				<div align="center">
					<img src="/env/grafics/kaskelix.png"/><br/>
					<h1><xsl:value-of select='channel/title' /></h1>				
				</div>	
				<p>
					<xsl:apply-templates select='channel/item' />
				</p>						
			</body>
		</html>
	</xsl:template>
	
	<xsl:template match='item'>
		<p>
			<b><xsl:value-of select='title'/></b><br/>
			<a href="{link}" ><xsl:value-of select='link' /></a><br/>
			<xsl:value-of select='pubDate' /><br/>
		</p>
	</xsl:template>
	
</xsl:stylesheet>