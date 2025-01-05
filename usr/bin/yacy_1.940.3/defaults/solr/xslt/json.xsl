<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>
 <xsl:output media-type="application/json" encoding="UTF-8" method="text"/>
 
 <xsl:template match='/'>
  <xsl:text>[</xsl:text>
  <xsl:for-each select="response/result/doc">
   <xsl:if test="position()&gt;1"><xsl:text>,</xsl:text></xsl:if>
   <xsl:apply-templates select="."/>
  </xsl:for-each>
  <xsl:text>]</xsl:text>
 </xsl:template>
 
 <xsl:template match="doc">
  <xsl:text>{"id":"</xsl:text><xsl:apply-templates select="str[@name='id']"/>
  <xsl:text>","url":"</xsl:text><xsl:apply-templates select="str[@name='sku']"/>
  <xsl:text>","title":"</xsl:text><xsl:apply-templates select="str[@name='title']"/>
  <xsl:text>"}</xsl:text>
 </xsl:template>
 
 <xsl:template match="str">
  <xsl:value-of select="translate(.,'&quot;',&apos;&quot;&apos;)"/>
 </xsl:template>
 
</xsl:stylesheet>
