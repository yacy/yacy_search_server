<?xml version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version='1.0'>
	<xsl:output method="html" />
	
	<xsl:template match="xbel">			
			<ul><xsl:apply-templates/></ul>
	</xsl:template>
	
	<!-- Only partial support for xbel elements -->
	<xsl:template match="xbel/info|xbel/title|xbel/desc|xbel/alias|xbel/separator">
		<!-- No op -->
	</xsl:template>
	
	<xsl:template match="folder">
		<li>
			<xsl:apply-templates select="title"/>
			<ul>
				<xsl:apply-templates select="folder|bookmark"/>
			</ul>
		</li>
	</xsl:template>
	
	<xsl:template match="folder/title">
		<span class="folder"><xsl:apply-templates/></span>
	</xsl:template>
	
	<xsl:template match="bookmark">
		<li>
			<a href="{@href}">
				<xsl:apply-templates select="title"/>
			</a>
		</li>
	</xsl:template>
	
	<xsl:template match="bookmark/title">
		<span class="file"><xsl:apply-templates/></span>
	</xsl:template>

</xsl:stylesheet>