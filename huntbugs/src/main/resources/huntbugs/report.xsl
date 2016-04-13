<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  xmlns:mml="http://www.w3.org/1998/Math/MathML"
  exclude-result-prefixes="xlink mml">

  <xsl:output doctype-public="-//W3C//DTD HTML 4.01 Transitional//EN"
    doctype-system="http://www.w3.org/TR/html4/loose.dtd"
    encoding="UTF-8"/>
 
  <xsl:param name="css" select="'report.css'"/>

  <xsl:template match="/">
    <html>
      <!-- HTML header -->
      <xsl:call-template name="make-html-header"/>
      <body>
        <xsl:apply-templates/>
      </body>
    </html>
  </xsl:template>

  <xsl:template name="make-html-header">
    <head>
      <title>
        HuntBugs Report
      </title>
      <link rel="stylesheet" type="text/css" href="{$css}"/>
    </head>
  </xsl:template>

  <xsl:template match="WarningList">
    <table class="Warnings"><thead><th colspan="2">Warnings</th></thead>
      <tbody>
        <xsl:apply-templates/>
      </tbody>
    </table>
  </xsl:template>

  <xsl:template match="Warning">
    <tr>
        <td rowspan="2">
            <div class="Title"><xsl:value-of select="Title"/></div>
            <table class="Properties">
            <tr><th>Category:</th><td><xsl:value-of select="@Category"/></td></tr>
            <tr><th>Score:</th><td><xsl:value-of select="@Score"/></td></tr>
            <xsl:apply-templates select="Location"/>
            <xsl:apply-templates select="Class"/>
            <xsl:apply-templates select="Method"/>
            </table>
        </td>
        <td class="Description">
            <div class="Description"><xsl:value-of select="Description"/></div>
        </td>
    </tr>
    <tr>
        <td>
            <div class="LongDescription"><xsl:value-of select="LongDescription/text()" disable-output-escaping="yes"/></div>
        </td>
    </tr>
  </xsl:template>

  <xsl:template match="Location">
    <tr><th>Location:</th><td><xsl:value-of select="@SourceFile"/>:<xsl:value-of select="@Line"/></td></tr>
  </xsl:template>

  <xsl:template match="Class">
    <tr><th>Class:</th><td><xsl:value-of select="@Name"/></td></tr>
  </xsl:template>

  <xsl:template match="Method">
    <tr><th>Method:</th><td><xsl:value-of select="@Name"/></td></tr>
  </xsl:template>
</xsl:stylesheet>
