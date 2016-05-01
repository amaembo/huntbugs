<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  exclude-result-prefixes="xlink">

  <xsl:output doctype-public="-//W3C//DTD HTML 4.01 Transitional//EN"
    doctype-system="http://www.w3.org/TR/html4/loose.dtd"
    encoding="UTF-8"/>
 
  <xsl:template match="/">
    <html>
      <!-- HTML header -->
      <xsl:call-template name="make-html-header"/>
      <body>
        <h1>HuntBugs report</h1>
        <xsl:apply-templates/>
      </body>
    </html>
  </xsl:template>

  <xsl:template name="make-html-header">
    <head>
      <title>
        HuntBugs Report
      </title>
      <style>
      body {
        font-family: verdana, helvetica, sans-serif;
      }

      br {
        margin: 1em;
      }
      
      code {
        font-size: 140%;
      }
      
      span.WarningType {
        font-size: 70%;
        color: gray;
      }
      
      code.Member {
        border-bottom: 1px gray dotted;
      }
      
      .AnotherLocation {
        color: gray;
      }
      
      table.Warnings, table.Errors {
        border-collapse: collapse;
        margin: 3pt;
      }
      
      table.Warnings, table.Warnings > tbody > tr > td {
        border: 1px solid blue;
        padding: 3pt;
      }

      table.Errors thead {
        background-color: red;
        color: white;
      }
      
      table.Errors, table.Errors > tbody > tr > td {
        border: 1px solid red;
        padding: 3pt;
      }
      
      table.Errors > tbody > tr > td {
        vertical-align: top;
      }
      
      .Title {
        font-weight: bold;
      }
      
      td.Description {
        background-color: yellow;
        height: 10pt;
        vertical-align: top;
      }
      
      table.Properties th {
        text-align: right;
        font-weight: normal;
        font-size: 80%;
        color: #444;
      }
      </style>
    </head>
  </xsl:template>

  <xsl:template match="ErrorList">
    <table class="Errors"><thead><tr><th colspan="2">Errors (<xsl:value-of select="count(Error)"/>)</th></tr></thead>
      <tbody>
        <xsl:apply-templates/>
      </tbody>
    </table>
  </xsl:template>

  <xsl:template match="Error">
    <tr>
        <td rowspan="2">
            <table class="Properties">
              <xsl:choose><xsl:when test="@Class"><tr><th>Class:</th><td><xsl:value-of select="@Class"/></td></tr></xsl:when></xsl:choose>
              <xsl:choose><xsl:when test="@Member"><tr><th>Member:</th><td><xsl:value-of select="@Member"/></td></tr></xsl:when></xsl:choose>
              <xsl:choose><xsl:when test="@Detector"><tr><th>Detector:</th><td><xsl:value-of select="@Detector"/></td></tr></xsl:when></xsl:choose>
            </table>
        </td>
        <td><pre><xsl:value-of select="."/></pre>
        </td>
    </tr>
  </xsl:template>
  
  <xsl:template match="WarningList">
    <table class="Warnings"><thead><tr><th colspan="2">Warnings (<xsl:value-of select="count(Warning)"/>)</th></tr></thead>
      <tbody>
        <xsl:apply-templates/>
      </tbody>
    </table>
  </xsl:template>

  <xsl:template match="Warning">
    <tr>
        <td rowspan="2">
            <div class="Title"><xsl:value-of select="Title"/><br/><span class="WarningType">(<xsl:value-of select="@Type"/>)</span></div>
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
    <tr><th>Location:</th><td><xsl:value-of select="@SourceFile"/>:<xsl:value-of select="@Line"/><xsl:apply-templates select="../AnotherLocation"/></td></tr>
  </xsl:template>

  <xsl:template match="AnotherLocation">
    <span class="AnotherLocation">; <xsl:value-of select="@Line"/></span>
  </xsl:template>

  <xsl:template match="Class">
    <tr><th>Class:</th><td><xsl:value-of select="@Name"/></td></tr>
  </xsl:template>

  <xsl:template match="Method">
    <tr><th>Method:</th><td><xsl:value-of select="@Name"/></td></tr>
  </xsl:template>
</xsl:stylesheet>
